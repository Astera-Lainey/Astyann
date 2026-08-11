package afb.astyann.documentservice.service;

import afb.astyann.documentservice.client.AIServiceClient;
import afb.astyann.documentservice.client.DiagramServiceClient;
import afb.astyann.documentservice.client.RAGServiceClient;
import afb.astyann.documentservice.client.RequirementServiceClient;
import afb.astyann.documentservice.client.VersionServiceClient;
import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import afb.astyann.documentservice.domain.DocumentVersionArchive;
import afb.astyann.documentservice.dto.ApiResponse;
import afb.astyann.documentservice.dto.DocumentContentDTO;
import afb.astyann.documentservice.dto.ValidationReportDTO;
import afb.astyann.documentservice.exception.DiagramsNotApprovedException;
import afb.astyann.documentservice.exception.DocumentContentNotAvailableException;
import afb.astyann.documentservice.exception.DocumentNotFoundException;
import afb.astyann.documentservice.exception.DocumentVersionNotFoundException;
import afb.astyann.documentservice.exception.DownstreamServiceException;
import afb.astyann.documentservice.exception.PcsfNotApprovedException;
import afb.astyann.documentservice.repository.DocumentRepository;
import afb.astyann.documentservice.repository.DocumentVersionArchiveRepository;
import afb.astyann.documentservice.service.schema.DocumentSchema;
import afb.astyann.documentservice.service.schema.DocumentSchemas;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentGenerationService {

    private static final Map<DocumentType, List<String>> DIAGRAM_EMBED_MAP = new EnumMap<>(DocumentType.class);
    static {
        DIAGRAM_EMBED_MAP.put(DocumentType.ARCHITECTURE_DOCUMENT, List.of("COMPONENT", "DEPLOYMENT"));
        DIAGRAM_EMBED_MAP.put(DocumentType.DESIGN_DOCUMENT, List.of("DESIGN_CLASS", "DESIGN_SEQUENCE", "COMPONENT", "PACKAGE"));
        DIAGRAM_EMBED_MAP.put(DocumentType.FUNCTIONAL_ANALYSIS, List.of("USE_CASE", "BUSINESS_SEQUENCE", "ACTIVITY"));
    }

    private final DocumentRepository repository;
    private final DocumentVersionArchiveRepository archiveRepository;
    private final RequirementServiceClient requirementServiceClient;
    private final DiagramServiceClient diagramServiceClient;
    private final RAGServiceClient ragServiceClient;
    private final AIServiceClient aiServiceClient;
    private final DocxMergeEngine mergeEngine;
    private final StorageService storageService;
    private final DocumentRagIndexingService ragIndexingService;
    private final VersionServiceClient versionServiceClient;
    private final DocumentValidationService validationService;
    private final ObjectMapper objectMapper;
    private final ApiContractDeriver apiContractDeriver;
    private final ApiContractOverlay apiContractOverlay;

    @Qualifier("documentExecutor")
    private final Executor documentExecutor;

    /**
     * Starts generation and returns immediately with every requested document type in
     * GENERATING status — it does not wait for the AI+merge pipeline to finish. Poll
     * GET /{projectId} until no document is left in GENERATING to find out how each one
     * turned out (PENDING_APPROVAL or FAILED, with lastError set on failure). Mirrors
     * DiagramGenerationService.startGeneration exactly, for the same reason: blocking until
     * every type finished routinely outlived the gateway's response timeout there, and
     * document generation's larger AI-JSON payloads make that at least as likely here.
     */
    public List<Document> startGeneration(UUID projectId, List<DocumentType> requestedTypes) {
        verifyPcsfApproved(projectId);
        verifyAllDiagramsApproved(projectId);

        List<DocumentType> types = (requestedTypes == null || requestedTypes.isEmpty())
                ? List.of(DocumentType.values())
                : requestedTypes;

        List<Document> placeholders = types.stream().map(t -> startOne(projectId, t)).toList();

        placeholders.forEach(placeholder -> documentExecutor.execute(() -> {
            try {
                generateOne(projectId, placeholder.getType());
            } catch (Exception ex) {
                log.error("Unexpected failure generating document type {}: {}",
                        placeholder.getType(), ex.getMessage(), ex);
                markFailed(placeholder, ex.getMessage());
            }
        }));

        return placeholders;
    }

    private Document startOne(UUID projectId, DocumentType type) {
        Document doc = repository.findByProjectIdAndType(projectId, type).orElseGet(Document::new);
        doc.setProjectId(projectId);
        doc.setType(type);
        doc.setStatus(DocumentStatus.GENERATING);
        doc.setLastError(null);
        return repository.save(doc);
    }

    public List<Document> getDocuments(UUID projectId, DocumentType type, DocumentStatus status) {
        return repository.findByProjectId(projectId).stream()
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> status == null || d.getStatus() == status)
                .toList();
    }

    public byte[] downloadDocument(UUID projectId, UUID documentId) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));
        if (doc.getPath() == null) {
            throw new DocumentNotFoundException(projectId, documentId);
        }
        return storageService.loadDocument(doc.getPath());
    }

    /**
     * Downloads a specific archived version, independent of whatever the document's current live
     * file is — unlike downloadDocument(), this never touches or is affected by
     * activateVersion(). Used by the version-history panel so "download vN" always returns vN's
     * actual content, no matter which version is currently active.
     */
    public byte[] downloadVersion(UUID projectId, UUID documentId, UUID snapshotId) {
        repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));

        DocumentVersionArchive archive = archiveRepository.findBySnapshotIdAndDocumentId(snapshotId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, snapshotId));
        if (archive.getFilePath() == null) {
            throw new DocumentVersionNotFoundException(documentId, snapshotId);
        }
        return storageService.loadDocument(archive.getFilePath());
    }

    /** The live structured content of a document. */
    public DocumentContentDTO getContent(UUID projectId, UUID documentId) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));
        return toContent(doc, doc.getContentJson(), doc.getSnapshotId());
    }

    /**
     * One archived version's content, independent of which version is currently live — the same
     * relationship {@code downloadVersion} has to {@code downloadDocument}.
     */
    public DocumentContentDTO getVersionContent(UUID projectId, UUID documentId, UUID snapshotId) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));

        DocumentVersionArchive archive = archiveRepository.findBySnapshotIdAndDocumentId(snapshotId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, snapshotId));
        return toContent(doc, archive.getContentJson(), snapshotId);
    }

    /**
     * The approved content for one document type — what a downstream generator reads.
     *
     * <p>No archive lookup is needed: {@code activateVersion} restores {@code contentJson} and
     * {@code snapshotId} together, so an APPROVED document's live content is by construction its
     * active snapshot's content.
     */
    public DocumentContentDTO getApprovedContent(UUID projectId, DocumentType type) {
        Document doc = repository.findByProjectIdAndType(projectId, type)
                .filter(d -> d.getStatus() == DocumentStatus.APPROVED)
                .orElseThrow(() -> new DocumentNotFoundException(projectId, type));
        return toContent(doc, doc.getContentJson(), doc.getSnapshotId());
    }

    private DocumentContentDTO toContent(Document doc, String json, UUID snapshotId) {
        if (json == null || json.isBlank()) {
            throw new DocumentContentNotAvailableException(doc.getDocumentId());
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            // Stored by this service from a node it had already parsed, so this should be
            // unreachable; treating it as "unavailable" beats handing back a broken payload.
            log.error("Stored content for documentId={} is not parseable as an object: {}",
                    doc.getDocumentId(), ex.getMessage());
            throw new DocumentContentNotAvailableException(doc.getDocumentId());
        }
        return DocumentContentDTO.builder()
                .documentId(doc.getDocumentId())
                .type(doc.getType())
                .status(doc.getStatus())
                .version(doc.getVersion())
                .snapshotId(snapshotId)
                .content(parsed)
                .build();
    }

    private Document generateOne(UUID projectId, DocumentType type) {
        Document doc = repository.findByProjectIdAndType(projectId, type).orElseGet(Document::new);
        doc.setProjectId(projectId);
        doc.setType(type);
        if (doc.getVersion() == null) doc.setVersion(1);

        DocumentSchema schema = DocumentSchemas.get(type);
        String context;
        try {
            context = ragServiceClient.getContext(projectId, DocumentPromptTemplates.ragQuery(type), 15);
        } catch (Exception ex) {
            return markFailed(doc, "Could not retrieve RAG context: " + ex.getMessage());
        }

        // Not indexed here: a PENDING_APPROVAL document has not been accepted by anyone, and may
        // never be. Indexing happens in approveDocument().
        return runGenerationPipeline(projectId, doc, schema,
                DocumentPromptTemplates.userPrompt(type, context, objectMapper));
    }

    /**
     * Regenerates an existing document using its stored change-request instructions. Documents
     * are schema-driven JSON merged into a .docx template with no reverse mapping back to that
     * JSON, so — unlike diagrams, which feed back their flat PlantUML source directly — this
     * reuses the exact same generation pipeline with the instructions appended as an explicit
     * directive, producing a full fresh regeneration guided by the feedback rather than a
     * literal incremental edit.
     */
    private Document generateWithFeedback(UUID projectId, DocumentType type, String instructions) {
        Document doc = repository.findByProjectIdAndType(projectId, type).orElseGet(Document::new);
        doc.setProjectId(projectId);
        doc.setType(type);
        if (doc.getVersion() == null) doc.setVersion(1);

        DocumentSchema schema = DocumentSchemas.get(type);
        String context;
        try {
            context = ragServiceClient.getContext(projectId, DocumentPromptTemplates.ragQuery(type), 15);
        } catch (Exception ex) {
            return markFailed(doc, "Could not retrieve RAG context: " + ex.getMessage());
        }

        String prompt = DocumentPromptTemplates.userPromptWithFeedback(type, context, objectMapper, instructions);
        Document result = runGenerationPipeline(projectId, doc, schema, prompt);
        if (result.getStatus() == DocumentStatus.PENDING_APPROVAL) {
            result.setChangeInstructions(null); // consumed on success; left intact on failure for retry
            result = repository.save(result);
            // Again, no indexing until approval. The previously approved text stays in the index
            // and remains the retrievable version until this regeneration is itself approved.
        }
        return result;
    }

    /** Shared AI-call/merge/embed/page-count/save pipeline used by both plain generation and
     * feedback-driven regeneration — everything after "what prompt to send" is identical. */
    private Document runGenerationPipeline(UUID projectId, Document doc, DocumentSchema schema, String userPrompt) {
        String rawJson;
        try {
            var response = aiServiceClient.infer(new AIServiceClient.InferBody(
                    DocumentPromptTemplates.MODEL, DocumentPromptTemplates.SYSTEM_PROMPT, userPrompt));
            rawJson = cleanJson(response.content());
        } catch (Exception ex) {
            return markFailed(doc, "AI generation failed: " + ex.getMessage());
        }

        JsonNode data;
        try {
            data = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            return markFailed(doc, "AI returned invalid JSON: " + ex.getMessage());
        }

        if (doc.getType() == DocumentType.API_CONTRACT) {
            applyDerivedApiContract(projectId, data);
        }

        byte[] docxBytes;
        try (InputStream template = new ClassPathResource(schema.templateResource()).getInputStream()) {
            byte[] merged = mergeEngine.merge(template, schema, data);
            docxBytes = embedDiagramsIfApplicable(projectId, doc.getType(), merged);
        } catch (Exception ex) {
            return markFailed(doc, "Document assembly failed: " + ex.getMessage());
        }

        doc.setStatus(DocumentStatus.PENDING_APPROVAL);
        doc.setLastError(null);
        // Serialised from the same node that was just merged — after applyDerivedApiContract, so
        // the stored JSON and the rendered .docx describe the same contract. Storing the model's
        // pre-overlay JSON would reintroduce exactly the document/code drift the overlay removes.
        doc.setContentJson(serialiseContent(data, doc));
        Document saved = repository.save(doc);

        String path;
        try {
            path = storageService.saveDocument(projectId, saved.getDocumentId(), docxBytes);
        } catch (Exception ex) {
            return markFailed(saved, "Could not store generated document: " + ex.getMessage());
        }

        saved.setPath(path);
        saved.setPageCount(estimatePageCount(docxBytes));
        return repository.save(saved);
    }

    /**
     * Serialises the merged content for storage. A failure here must not fail the generation —
     * the {@code .docx} is already built and is what the user asked for; losing the structured
     * copy degrades a downstream consumer rather than this request.
     */
    private String serialiseContent(JsonNode data, Document doc) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            log.warn("Could not store structured content for documentId={} type={}: {}",
                    doc.getDocumentId(), doc.getType(), ex.getMessage());
            return null;
        }
    }

    /**
     * Overwrites the API contract's endpoint tables with the operations the code generator will
     * actually emit, derived from the same {@code pcsf.endpoints} the generator reads.
     *
     * <p>Best-effort on purpose: if the PCSF cannot be fetched or yields no endpoints, the
     * document keeps the model's own tables. A contract written from prose is worth less than one
     * derived from the declaration, but it is worth considerably more than a failed generation.
     */
    private void applyDerivedApiContract(UUID projectId, JsonNode data) {
        try {
            var response = requirementServiceClient.getPcsf(projectId);
            var pcsf = response != null ? response.getData() : null;
            if (pcsf == null) {
                log.warn("API contract for project {} left as authored — PCSF unavailable", projectId);
                return;
            }
            var derived = apiContractDeriver.derive(pcsf);
            if (derived.isEmpty()) {
                log.warn("API contract for project {} left as authored — PCSF declares no modules", projectId);
                return;
            }
            var unmatched = apiContractOverlay.unmatchedAuthoredKeys(data, derived);
            if (!unmatched.isEmpty()) {
                log.info("API contract for project {}: model described {} operation(s) the generator "
                         + "does not emit, replaced by the derived contract: {}",
                        projectId, unmatched.size(), unmatched);
            }
            apiContractOverlay.apply(data, derived);
        } catch (Exception ex) {
            log.warn("API contract for project {} left as authored — could not derive from PCSF: {}",
                    projectId, ex.getMessage());
        }
    }

    public record ApproveOutcome(Document document, ValidationReportDTO report, UUID snapshotId, boolean allDocumentsApproved) {}

    /**
     * Validates the document (throws on a blocking check failure), approves it, and creates a
     * version snapshot. This deliberately deviates from the "Validate Generated Documents"
     * sequence diagram (which shows the snapshot happening on regenerate) for consistency with
     * DiagramGeneratorService, where the snapshot is created on approve.
     */
    @Transactional
    public ApproveOutcome approveDocument(UUID projectId, UUID documentId, String validationNote) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));

        if (doc.getStatus() == DocumentStatus.APPROVED) {
            throw new IllegalStateException("Document " + documentId + " is already approved.");
        }
        if (doc.getStatus() != DocumentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Document must be PENDING_APPROVAL to approve; current status is " + doc.getStatus());
        }

        ValidationReportDTO report = validationService.validate(doc);

        doc.setStatus(DocumentStatus.APPROVED);
        doc.setChangeInstructions(null);
        repository.save(doc);

        String reason = (validationNote != null && !validationNote.isBlank()) ? validationNote : "Document approved";
        UUID snapshotId = attemptSnapshot(projectId, doc, reason);
        repository.save(doc);

        // Only now does this text become the project's accepted version, so only now does it
        // belong in the retrieval index. RAGService replaces by sourceId, so this supersedes any
        // previously approved text for the same document rather than piling up beside it.
        indexAfterCommit(doc.getDocumentId());

        // Deliberately not calling ProjectService to update project status here — same reasoning
        // as DiagramGenerationService.approve(): ProjectStatus has no per-stage "documents
        // approved" value.

        boolean allApproved = repository.findByProjectId(projectId).stream()
                .allMatch(d -> d.getStatus() == DocumentStatus.APPROVED);

        return new ApproveOutcome(doc, report, snapshotId, allApproved);
    }

    /**
     * Records free-text change instructions for a document, resetting it to PENDING_APPROVAL
     * (undoing APPROVED or FAILED) so it's ready to be regenerated with that feedback. This is
     * the only way to move an APPROVED document back into an editable state — regenerateDocument()
     * rejects APPROVED documents outright, so callers must come through here first.
     */
    @Transactional
    public Document submitChangeRequest(UUID projectId, UUID documentId, String instructions) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));
        doc.setChangeInstructions(instructions);
        doc.setStatus(DocumentStatus.PENDING_APPROVAL);
        return repository.save(doc);
    }

    public record RegenerateResult(Document document, UUID previousVersionId) {}

    /**
     * Kicks off regeneration for a single, already-existing document and returns immediately —
     * it does NOT wait for the AI+merge pipeline to finish. Mirrors startGeneration()'s async
     * pattern: blocking here until the pipeline finished routinely outlived the gateway's
     * response-timeout (documents' larger AI-JSON payloads make this even more likely than for
     * diagrams). Poll GET /{projectId} until the document is no longer GENERATING to see the
     * outcome.
     */
    public RegenerateResult regenerateDocument(UUID projectId, UUID documentId) {
        Document existing = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));

        if (existing.getStatus() == DocumentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot regenerate an APPROVED document directly. Submit a change-request first.");
        }
        if (existing.getStatus() == DocumentStatus.GENERATING) {
            throw new IllegalStateException("Document is still generating. Please wait for it to finish.");
        }

        verifyPcsfApproved(projectId);
        verifyAllDiagramsApproved(projectId);

        UUID previousVersionId = findActiveSnapshotId(projectId, documentId);
        int nextVersion = (existing.getVersion() == null ? 1 : existing.getVersion()) + 1;
        String instructions = existing.getChangeInstructions();
        DocumentType type = existing.getType();

        existing.setStatus(DocumentStatus.GENERATING);
        existing.setLastError(null);
        Document placeholder = repository.save(existing);

        documentExecutor.execute(() -> {
            try {
                Document result = (instructions != null && !instructions.isBlank())
                        ? generateWithFeedback(projectId, type, instructions)
                        : generateOne(projectId, type);
                if (result.getStatus() != DocumentStatus.FAILED) {
                    result.setVersion(nextVersion);
                    repository.save(result);
                }
            } catch (Exception ex) {
                log.error("Unexpected failure regenerating documentId={}: {}", documentId, ex.getMessage(), ex);
                markFailed(placeholder, ex.getMessage());
            }
        });

        return new RegenerateResult(placeholder, previousVersionId);
    }

    /**
     * Best-effort: creates a VersionService snapshot for an already-APPROVED document and, on
     * success, stamps its snapshotId so it's no longer picked up by retryPendingSnapshots().
     * Returns null (and just logs) on failure — callers are expected to tolerate that and let
     * the retry job catch up later rather than fail/rollback the approval itself.
     */
    private UUID attemptSnapshot(UUID projectId, Document doc, String triggerReason) {
        try {
            var snap = versionServiceClient.createSnapshot(projectId, new VersionServiceClient.CreateSnapshotRequest(
                    "DOCUMENT", null, null, triggerReason, doc.getPath(), doc.getDocumentId(), doc.getType().name()));
            if (snap != null && snap.getData() != null) {
                UUID snapId = snap.getData().snapId();
                doc.setSnapshotId(snapId);
                // Captures the .docx path as it exists right now so activateVersion() has
                // something to restore later — Document itself only ever holds the current
                // live file.
                archiveRepository.save(DocumentVersionArchive.builder()
                        .snapshotId(snapId)
                        .projectId(projectId)
                        .documentId(doc.getDocumentId())
                        .filePath(doc.getPath())
                        .contentJson(doc.getContentJson())
                        .pageCount(doc.getPageCount())
                        .build());
                return snapId;
            }
        } catch (Exception ex) {
            log.error("Snapshot creation failed for documentId={}: {}", doc.getDocumentId(), ex.getMessage());
        }
        return null;
    }

    /**
     * Restores an archived (previously-approved) version as the document's current live file —
     * a real rollback, not just a VersionService metadata flip. The restored document becomes
     * APPROVED immediately (it was approved once already). Also flips the active flag on the
     * VersionService side (best-effort) so the version-history timeline stays consistent.
     */
    @Transactional
    public Document activateVersion(UUID projectId, UUID documentId, UUID snapshotId) {
        Document doc = repository.findById(documentId)
                .filter(d -> d.getProjectId().equals(projectId))
                .orElseThrow(() -> new DocumentNotFoundException(projectId, documentId));

        DocumentVersionArchive archive = archiveRepository.findBySnapshotIdAndDocumentId(snapshotId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(documentId, snapshotId));

        doc.setPath(archive.getFilePath());
        // Restored together with the file, or the live JSON would go on describing the version
        // this restore exists to undo.
        doc.setContentJson(archive.getContentJson());
        doc.setPageCount(archive.getPageCount());
        doc.setStatus(DocumentStatus.APPROVED);
        doc.setLastError(null);
        doc.setChangeInstructions(null);
        doc.setSnapshotId(snapshotId);
        Document saved = repository.save(doc);

        try {
            versionServiceClient.activateSnapshot(snapshotId);
        } catch (Exception ex) {
            log.warn("Restored documentId={} to snapshotId={} but could not flip its active flag " +
                    "in VersionService: {}", documentId, snapshotId, ex.getMessage());
        }

        // The live file just changed. Without this the index would keep serving the text of the
        // version that was rolled back — the exact case a restore is meant to undo.
        indexAfterCommit(documentId);

        return saved;
    }

    /**
     * Sweeps every APPROVED document still missing a snapshotId (approveDocument() recorded the
     * approval but the outbound createSnapshot call failed) and retries it. Invoked on a fixed
     * schedule by DocumentSnapshotRetryScheduler.
     */
    @Transactional
    public void retryPendingSnapshots() {
        List<Document> pending = repository.findByStatusAndSnapshotIdIsNull(DocumentStatus.APPROVED);
        if (pending.isEmpty()) return;

        log.info("Retrying snapshot creation for {} approved document(s) missing a snapshot", pending.size());
        for (Document d : pending) {
            if (attemptSnapshot(d.getProjectId(), d, "Document approved (retried snapshot creation)") != null) {
                repository.save(d);
                // Approval already indexed this text, but with an empty snapshotId because the
                // snapshot did not exist yet. Re-index so the stored chunks name the version they
                // actually came from.
                indexAfterCommit(d.getDocumentId());
            }
        }
    }

    /**
     * Schedules RAG indexing for after the current transaction commits.
     *
     * <p>The indexer runs on another thread and re-reads the document to pick up its path and
     * snapshotId. Firing it inside the transaction would race the commit: the indexing thread can
     * reach the database first and index the document's pre-approval state — or, if the
     * transaction then rolls back, index a version that never existed. Falls back to indexing
     * immediately when there is no active transaction.
     */
    private void indexAfterCommit(UUID documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ragIndexingService.indexApprovedDocumentAsync(documentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ragIndexingService.indexApprovedDocumentAsync(documentId);
            }
        });
    }

    private UUID findActiveSnapshotId(UUID projectId, UUID documentId) {
        try {
            var response = versionServiceClient.listSnapshots(projectId);
            if (response == null || response.getData() == null) return null;
            return response.getData().stream()
                    .filter(s -> documentId.equals(s.documentId()) && s.active())
                    .map(VersionServiceClient.SnapshotDTO::snapId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Could not look up previous version for documentId={}: {}", documentId, ex.getMessage());
            return null;
        }
    }

    private byte[] embedDiagramsIfApplicable(UUID projectId, DocumentType type, byte[] docxBytes) throws Exception {
        List<String> toEmbed = DIAGRAM_EMBED_MAP.getOrDefault(type, List.of());
        if (toEmbed.isEmpty()) return docxBytes;

        List<DiagramServiceClient.DiagramItem> diagrams;
        try {
            var response = diagramServiceClient.list(projectId);
            diagrams = response != null && response.getData() != null ? response.getData().diagrams() : List.of();
        } catch (Exception ex) {
            log.warn("Could not list diagrams for project {} — skipping diagram embedding: {}", projectId, ex.getMessage());
            return docxBytes;
        }

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            XWPFParagraph heading = document.createParagraph();
            heading.createRun().setText("Appendix: Diagrams");

            for (String diagramType : toEmbed) {
                diagrams.stream()
                        .filter(d -> diagramType.equals(d.type()) && "APPROVED".equals(d.status()))
                        .findFirst()
                        .ifPresent(d -> embedOne(document, diagramType, d.diagramId(), projectId));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private void embedOne(XWPFDocument document, String diagramType, UUID diagramId, UUID projectId) {
        try {
            byte[] png = diagramServiceClient.render(projectId, diagramId, "PNG");
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) return;
            int width = Math.min(img.getWidth(), 600);
            int height = (int) ((double) width / img.getWidth() * img.getHeight());

            document.createParagraph().createRun().setText(diagramType.replace('_', ' ') + " Diagram");
            document.createParagraph().createRun().addPicture(new ByteArrayInputStream(png),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, diagramType + ".png",
                    Units.pixelToEMU(width), Units.pixelToEMU(height));
        } catch (Exception ex) {
            log.warn("Could not embed {} diagram for project {}: {}", diagramType, projectId, ex.getMessage());
        }
    }

    private Integer estimatePageCount(byte[] docxBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            int chars = document.getParagraphs().stream().mapToInt(p -> p.getText().length()).sum();
            return Math.max(1, chars / 3000);
        } catch (Exception ex) {
            return null;
        }
    }

    private Document markFailed(Document doc, String error) {
        doc.setStatus(DocumentStatus.FAILED);
        doc.setLastError(error);
        return repository.save(doc);
    }

    private String cleanJson(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?s)```json\\s*", "")
                   .replaceAll("(?s)```\\s*", "")
                   .trim();
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

    private void verifyAllDiagramsApproved(UUID projectId) {
        ApiResponse<DiagramServiceClient.DiagramListData> response;
        try {
            response = diagramServiceClient.list(projectId);
        } catch (Exception ex) {
            throw new DownstreamServiceException("Could not reach DiagramGeneratorService.", ex);
        }
        List<DiagramServiceClient.DiagramItem> diagrams =
                response != null && response.getData() != null ? response.getData().diagrams() : List.of();
        if (diagrams.isEmpty() || diagrams.stream().anyMatch(d -> !"APPROVED".equals(d.status()))) {
            throw new DiagramsNotApprovedException(projectId);
        }
    }
}
