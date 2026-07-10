package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.client.AIServiceClient;
import afb.astyann.diagramgeneratorservice.client.KrokiClient;
import afb.astyann.diagramgeneratorservice.client.RAGServiceClient;
import afb.astyann.diagramgeneratorservice.client.RequirementServiceClient;
import afb.astyann.diagramgeneratorservice.client.VersionServiceClient;
import afb.astyann.diagramgeneratorservice.domain.DiagramStatus;
import afb.astyann.diagramgeneratorservice.domain.DiagramType;
import afb.astyann.diagramgeneratorservice.domain.UMLDiagram;
import afb.astyann.diagramgeneratorservice.dto.ApiResponse;
import afb.astyann.diagramgeneratorservice.dto.GenerateDiagramsRequest;
import afb.astyann.diagramgeneratorservice.exception.DiagramNotFoundException;
import afb.astyann.diagramgeneratorservice.exception.DownstreamServiceException;
import afb.astyann.diagramgeneratorservice.exception.InvalidRenderFormatException;
import afb.astyann.diagramgeneratorservice.exception.PcsfNotApprovedException;
import afb.astyann.diagramgeneratorservice.repository.UMLDiagramRepository;
import afb.astyann.diagramgeneratorservice.util.PlantUmlCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagramGenerationService {

    private static final Set<String> VALID_FORMATS = Set.of("SVG", "PNG");
    private static final int MAX_RENDER_ATTEMPTS = 2;

    private final UMLDiagramRepository repository;
    private final RequirementServiceClient requirementServiceClient;
    private final RAGServiceClient ragServiceClient;
    private final AIServiceClient aiServiceClient;
    private final KrokiClient krokiClient;
    private final StorageService storageService;
    private final VersionServiceClient versionServiceClient;
    private final RagIndexingService ragIndexingService;

    @Qualifier("diagramExecutor")
    private final Executor diagramExecutor;

    /**
     * Kicks off generation for each requested diagram type and returns immediately — it does
     * NOT wait for the AI+Kroki pipelines to finish. Each type's row is upserted to GENERATING
     * synchronously before dispatch, so it's visible via getDiagrams()/list the instant this
     * method returns; callers poll that endpoint until no diagram is left in GENERATING.
     * (Previously this blocked on CompletableFuture.allOf(...).join() until every type
     * succeeded or failed, which could run for minutes across 10 types and routinely outlived
     * the gateway's response-timeout, aborting the client connection before the response could
     * be flushed.)
     */
    public List<UMLDiagram> startGeneration(UUID projectId, GenerateDiagramsRequest request) {
        verifyPcsfApproved(projectId);

        String format = normalizeFormat(request.getRenderFormat());
        List<DiagramType> types = (request.getDiagramTypes() == null || request.getDiagramTypes().isEmpty())
                ? List.of(DiagramType.values())
                : request.getDiagramTypes();

        List<UMLDiagram> placeholders = types.stream()
                .map(type -> startOne(projectId, type, format))
                .toList();

        placeholders.forEach(placeholder -> diagramExecutor.execute(() -> {
            try {
                generateOne(projectId, placeholder.getType(), format);
            } catch (Exception ex) {
                // generateOne() persists its own failures internally; this only catches
                // something truly unexpected that slipped past that handling (e.g. a bug).
                log.error("Unexpected failure generating diagram type {}: {}",
                        placeholder.getType(), ex.getMessage(), ex);
                markFailed(placeholder, ex.getMessage());
            }
        }));

        return placeholders;
    }

    private UMLDiagram startOne(UUID projectId, DiagramType type, String format) {
        UMLDiagram diagram = repository.findByProjectIdAndType(projectId, type).orElseGet(UMLDiagram::new);
        diagram.setProjectId(projectId);
        diagram.setType(type);
        diagram.setRenderFormat(format);
        diagram.setStatus(DiagramStatus.GENERATING);
        diagram.setLastError(null);
        return repository.save(diagram);
    }

    public record RegenerateResult(UMLDiagram diagram, UUID previousVersionId) {}

    /**
     * Re-runs generation for a single, already-existing diagram (by id) — used to retry a
     * diagram that previously ended up FAILED, or to apply feedback recorded via change-request.
     * If the diagram has stored change-request instructions, applies them (feedback-driven
     * regeneration) instead of a plain from-scratch regeneration, and clears them on success.
     * Rejects APPROVED diagrams outright — a change-request must be submitted first, which
     * resets the diagram to PENDING_APPROVAL and is what actually allows this to proceed.
     */
    public RegenerateResult regenerateDiagram(UUID projectId, UUID diagramId, String formatOverride) {
        UMLDiagram existing = repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));

        if (existing.getStatus() == DiagramStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot regenerate an APPROVED diagram directly. Submit a change-request first.");
        }
        if (existing.getStatus() == DiagramStatus.GENERATING) {
            throw new IllegalStateException("Diagram is still generating. Please wait for it to finish.");
        }

        verifyPcsfApproved(projectId);

        String format = normalizeFormat(formatOverride != null ? formatOverride : existing.getRenderFormat());
        UUID previousVersionId = findActiveSnapshotId(projectId, diagramId);

        String instructions = existing.getChangeInstructions();
        UMLDiagram result = (instructions != null && !instructions.isBlank())
                ? generateWithFeedback(projectId, existing, format, instructions)
                : generateOne(projectId, existing.getType(), format);

        return new RegenerateResult(result, previousVersionId);
    }

    public record ApproveOutcome(List<UUID> snapshotIds, int updatedCount, boolean allDiagramsApproved) {}

    /**
     * Approves the given diagrams (or all of the project's diagrams if none specified),
     * recording one version snapshot per approved diagram and asynchronously indexing each
     * approved diagram's content into RAG for later stages to retrieve as context.
     */
    @Transactional
    public ApproveOutcome approve(UUID projectId, List<UUID> diagramIds, String approvalComment) {
        List<UMLDiagram> projectDiagrams = repository.findByProjectId(projectId);
        if (projectDiagrams.isEmpty()) {
            throw new DiagramNotFoundException(projectId);
        }

        List<UMLDiagram> targets = (diagramIds == null || diagramIds.isEmpty())
                ? projectDiagrams
                : projectDiagrams.stream().filter(d -> diagramIds.contains(d.getDiagramId())).toList();

        List<UMLDiagram> approvable = targets.stream()
                .filter(d -> d.getStatus() == DiagramStatus.PENDING_APPROVAL)
                .toList();
        if (approvable.isEmpty()) {
            throw new IllegalStateException(
                    "No diagrams in PENDING_APPROVAL state to approve for project " + projectId);
        }

        approvable.forEach(d -> {
            d.setStatus(DiagramStatus.APPROVED);
            d.setChangeInstructions(null);
        });
        repository.saveAll(approvable);

        // Synchronous: this is a pure outbound POST carrying in-memory diagram fields, not a
        // read of this service's own DB, so there's no pre-commit staleness risk to defer for.
        List<UUID> snapshotIds = new ArrayList<>();
        for (UMLDiagram d : approvable) {
            try {
                var snap = versionServiceClient.createSnapshot(projectId, new VersionServiceClient.CreateSnapshotRequest(
                        "DIAGRAM",
                        d.getType().name(),
                        null,
                        (approvalComment != null && !approvalComment.isBlank()) ? approvalComment : "Diagram approved",
                        d.getGeneratedImagePath(),
                        d.getDiagramId(),
                        d.getType().name()));
                if (snap != null && snap.getData() != null) {
                    snapshotIds.add(snap.getData().snapId());
                }
            } catch (Exception ex) {
                log.error("Snapshot creation failed for diagramId={} — approval still recorded: {}",
                        d.getDiagramId(), ex.getMessage());
            }
        }

        // Deliberately not calling ProjectService to update project status here — ProjectStatus
        // (ANALYZING/GENERATING/COMPLETED/FAILED) has no per-stage "diagrams approved" value and
        // no granularity for it.

        // Async, afterCommit-deferred RAG indexing (mirrors RequirementService.approve()) — this
        // DOES read back diagram state from this service's DB, so it must wait for the commit.
        List<UUID> idsForIndexing = approvable.stream().map(UMLDiagram::getDiagramId).toList();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    idsForIndexing.forEach(ragIndexingService::indexApprovedDiagramAsync);
                }
            });
        } else {
            idsForIndexing.forEach(ragIndexingService::indexApprovedDiagramAsync);
        }

        boolean allApproved = repository.findByProjectId(projectId).stream()
                .allMatch(d -> d.getStatus() == DiagramStatus.APPROVED);

        return new ApproveOutcome(snapshotIds, approvable.size(), allApproved);
    }

    /**
     * Records free-text change instructions for a diagram, resetting it to PENDING_APPROVAL
     * (undoing APPROVED or FAILED) so it's ready to be regenerated with that feedback. This is
     * the only way to move an APPROVED diagram back into an editable state — regenerate()
     * rejects APPROVED diagrams outright, so callers must come through here first.
     */
    @Transactional
    public UMLDiagram submitChangeRequest(UUID projectId, UUID diagramId, String instructions) {
        UMLDiagram diagram = repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));

        diagram.setChangeInstructions(instructions);
        diagram.setStatus(DiagramStatus.PENDING_APPROVAL);
        return repository.save(diagram);
    }

    private UUID findActiveSnapshotId(UUID projectId, UUID diagramId) {
        try {
            var response = versionServiceClient.listSnapshots(projectId);
            if (response == null || response.getData() == null) return null;
            return response.getData().stream()
                    .filter(s -> diagramId.equals(s.diagramId()) && s.active())
                    .map(VersionServiceClient.SnapshotDTO::snapId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Could not look up previous version for diagramId={}: {}", diagramId, ex.getMessage());
            return null;
        }
    }

    public List<UMLDiagram> getDiagrams(UUID projectId, DiagramType type, DiagramStatus status) {
        return repository.findByProjectId(projectId).stream()
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> status == null || d.getStatus() == status)
                .toList();
    }

    public UMLDiagram getDiagram(UUID projectId, UUID diagramId) {
        return repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));
    }

    public byte[] renderDiagram(UUID projectId, UUID diagramId, String format) {
        String normalized = normalizeFormat(format);
        UMLDiagram diagram = repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));

        if (normalized.equalsIgnoreCase(diagram.getRenderFormat()) && diagram.getGeneratedImagePath() != null) {
            return storageService.loadImage(diagram.getGeneratedImagePath());
        }
        // Requested format differs from what's cached on disk — re-render on the fly, don't persist.
        return krokiClient.render(diagram.getSourceCode(), normalized);
    }

    private UMLDiagram generateOne(UUID projectId, DiagramType type, String format) {
        UMLDiagram diagram = repository.findByProjectIdAndType(projectId, type).orElseGet(UMLDiagram::new);
        diagram.setProjectId(projectId);
        diagram.setType(type);
        diagram.setRenderFormat(format);

        String context;
        try {
            context = ragServiceClient.getContext(projectId, DiagramPromptTemplates.ragQuery(type), 10);
        } catch (Exception ex) {
            return markFailed(diagram, "Could not retrieve RAG context: " + ex.getMessage());
        }

        return generateAndPersist(diagram, type, format, DiagramPromptTemplates.userPrompt(type, context), null);
    }

    /**
     * Regenerates an existing diagram using its stored change-request instructions, feeding the
     * current source + fresh RAG context + the requested changes into one prompt, and clearing
     * the instructions on success (they're consumed) while leaving them intact on failure.
     */
    private UMLDiagram generateWithFeedback(UUID projectId, UMLDiagram existing, String format, String instructions) {
        String context;
        try {
            context = ragServiceClient.getContext(projectId, DiagramPromptTemplates.ragQuery(existing.getType()), 10);
        } catch (Exception ex) {
            return markFailed(existing, "Could not retrieve RAG context: " + ex.getMessage());
        }

        String prompt = DiagramPromptTemplates.changeRequestPrompt(
                existing.getType(), existing.getSourceCode(), context, instructions);
        existing.setRenderFormat(format);
        return generateAndPersist(existing, existing.getType(), format, prompt, () -> existing.setChangeInstructions(null));
    }

    private UMLDiagram generateAndPersist(UMLDiagram diagram, DiagramType type, String format,
                                           String userPrompt, Runnable onSuccess) {
        String initialSource;
        try {
            initialSource = inferPlantUml(type, userPrompt);
        } catch (Exception ex) {
            return markFailed(diagram, ex.getMessage());
        }

        RenderResult result;
        try {
            result = renderWithSelfCorrection(type, initialSource, format);
        } catch (Exception ex) {
            diagram.setSourceCode(initialSource); // keep the last-attempted source for debugging
            return markFailed(diagram, ex.getMessage());
        }

        diagram.setStatus(DiagramStatus.PENDING_APPROVAL);
        diagram.setSourceCode(result.source());
        diagram.setLastError(null);
        if (onSuccess != null) onSuccess.run();
        UMLDiagram saved = repository.save(diagram);

        String path = storageService.saveImage(diagram.getProjectId(), saved.getDiagramId(), format, result.image());
        saved.setGeneratedImagePath(path);
        return repository.save(saved);
    }

    private UMLDiagram markFailed(UMLDiagram diagram, String error) {
        diagram.setStatus(DiagramStatus.FAILED);
        diagram.setLastError(error);
        return repository.save(diagram);
    }

    private String inferPlantUml(DiagramType type, String userPrompt) {
        try {
            var response = aiServiceClient.infer(new AIServiceClient.InferBody(
                    DiagramPromptTemplates.MODEL, DiagramPromptTemplates.SYSTEM_PROMPT, userPrompt));
            return PlantUmlCleaner.clean(response.content());
        } catch (Exception ex) {
            throw new DownstreamServiceException("AI generation failed for " + type, ex);
        }
    }

    private record RenderResult(String source, byte[] image) {}

    /**
     * On a Kroki rejection, feeds the exact renderer error back to the model and asks it to
     * fix just the syntax, then retries once — a small local model occasionally hallucinates
     * a nonexistent keyword or drops a relationship connector, and this self-corrects those
     * cases without failing the whole diagram type outright. Returns the source that actually
     * rendered successfully, since a retry may have changed it.
     */
    private RenderResult renderWithSelfCorrection(DiagramType type, String source, String format) {
        String currentSource = source;
        DownstreamServiceException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RENDER_ATTEMPTS; attempt++) {
            try {
                byte[] image = krokiClient.render(currentSource, format);
                return new RenderResult(currentSource, image);
            } catch (DownstreamServiceException ex) {
                lastFailure = ex;
            } catch (Exception ex) {
                lastFailure = new DownstreamServiceException("Rendering failed for " + type, ex);
            }
            if (attempt < MAX_RENDER_ATTEMPTS) {
                log.info("Retrying {} generation after Kroki rejection (attempt {}): {}", type, attempt, lastFailure.getMessage());
                currentSource = inferPlantUml(type, DiagramPromptTemplates.fixPrompt(type, currentSource, lastFailure.getMessage()));
            }
        }
        throw lastFailure;
    }

    private void verifyPcsfApproved(UUID projectId) {
        ApiResponse<RequirementServiceClient.PcsfStatusPayload> response;
        try {
            response = requirementServiceClient.getPcsfStatus(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach RequirementService.", ex);
        }
        if (response == null || response.getData() == null
                || !"APPROVED".equals(response.getData().pcsfStatus())) {
            throw new PcsfNotApprovedException(projectId);
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return "SVG";
        String upper = format.trim().toUpperCase();
        if (!VALID_FORMATS.contains(upper)) {
            throw new InvalidRenderFormatException(format);
        }
        return upper;
    }
}
