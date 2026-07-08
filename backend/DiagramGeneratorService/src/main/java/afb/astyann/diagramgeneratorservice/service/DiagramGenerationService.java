package afb.astyann.diagramgeneratorservice.service;

import afb.astyann.diagramgeneratorservice.client.AIServiceClient;
import afb.astyann.diagramgeneratorservice.client.KrokiClient;
import afb.astyann.diagramgeneratorservice.client.RAGServiceClient;
import afb.astyann.diagramgeneratorservice.client.RequirementServiceClient;
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

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    @Qualifier("diagramExecutor")
    private final Executor diagramExecutor;

    public record GenerationOutcome(List<UMLDiagram> succeeded, List<UMLDiagram> failed) {}

    public GenerationOutcome generateDiagrams(UUID projectId, GenerateDiagramsRequest request) {
        verifyPcsfApproved(projectId);

        String format = normalizeFormat(request.getRenderFormat());
        List<DiagramType> types = (request.getDiagramTypes() == null || request.getDiagramTypes().isEmpty())
                ? List.of(DiagramType.values())
                : request.getDiagramTypes();

        record TypedFuture(DiagramType type, CompletableFuture<UMLDiagram> future) {}

        List<TypedFuture> tasks = types.stream()
                .map(type -> new TypedFuture(type, CompletableFuture.supplyAsync(
                        () -> generateOne(projectId, type, format), diagramExecutor)))
                .toList();

        // Wait for EVERY task to finish (success or failure) before inspecting any of them.
        // Inspecting results one-by-one via .join() in list order would throw on the first
        // failure and abandon the stream — but the still-running tasks were already dispatched
        // to the executor and keep mutating the DB/filesystem in the background regardless,
        // landing writes well after this method (and the HTTP response) has already returned.
        CompletableFuture.allOf(tasks.stream().map(TypedFuture::future).toArray(CompletableFuture[]::new))
                .exceptionally(ex -> null)
                .join();

        List<UMLDiagram> succeeded = new ArrayList<>();
        List<UMLDiagram> failed = new ArrayList<>();
        for (TypedFuture task : tasks) {
            try {
                UMLDiagram diagram = task.future().join();
                if (diagram.getStatus() == DiagramStatus.FAILED) {
                    failed.add(diagram);
                } else {
                    succeeded.add(diagram);
                }
            } catch (CompletionException ex) {
                // generateOne() persists its own failures below; this only catches something
                // truly unexpected that slipped past its internal handling (e.g. a bug), so
                // there's no persisted row/diagramId to report here.
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                log.error("Unexpected failure generating diagram type {}: {}", task.type(), cause.getMessage(), cause);
            }
        }

        if (succeeded.isEmpty() && !failed.isEmpty()) {
            throw new DownstreamServiceException("All diagram generations failed.");
        }
        return new GenerationOutcome(succeeded, failed);
    }

    /**
     * Re-runs generation for a single, already-existing diagram (by id) — used to retry a
     * diagram that previously ended up FAILED, or simply to refresh any existing diagram.
     * Reuses generateOne()'s upsert-by-type logic, keyed off the existing row's type.
     */
    public UMLDiagram regenerateDiagram(UUID projectId, UUID diagramId, String formatOverride) {
        UMLDiagram existing = repository.findById(diagramId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DiagramNotFoundException(projectId, diagramId));

        verifyPcsfApproved(projectId);

        String format = normalizeFormat(formatOverride != null ? formatOverride : existing.getRenderFormat());
        return generateOne(projectId, existing.getType(), format);
    }

    public List<UMLDiagram> getDiagrams(UUID projectId, DiagramType type, DiagramStatus status) {
        return repository.findByProjectId(projectId).stream()
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> status == null || d.getStatus() == status)
                .toList();
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

        String initialSource;
        try {
            initialSource = inferPlantUml(type, DiagramPromptTemplates.userPrompt(type, context));
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
        UMLDiagram saved = repository.save(diagram);

        String path = storageService.saveImage(projectId, saved.getDiagramId(), format, result.image());
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
