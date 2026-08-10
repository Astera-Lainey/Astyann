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
import afb.astyann.documentservice.dto.InferenceResponseDTO;
import afb.astyann.documentservice.repository.DocumentRepository;
import afb.astyann.documentservice.repository.DocumentVersionArchiveRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the content's life beyond the moment it is generated: that what gets stored is what was
 * merged, that a snapshot captures it alongside the .docx it produced, and that a rollback restores
 * both together.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentContentLifecycleTest {

    @Mock private DocumentRepository repository;
    @Mock private DocumentVersionArchiveRepository archiveRepository;
    @Mock private RequirementServiceClient requirementServiceClient;
    @Mock private DiagramServiceClient diagramServiceClient;
    @Mock private RAGServiceClient ragServiceClient;
    @Mock private AIServiceClient aiServiceClient;
    @Mock private DocxMergeEngine mergeEngine;
    @Mock private StorageService storageService;
    @Mock private DocumentRagIndexingService ragIndexingService;
    @Mock private VersionServiceClient versionServiceClient;
    @Mock private DocumentValidationService validationService;
    @Mock private ApiContractDeriver apiContractDeriver;

    private DocumentGenerationService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID DOC = UUID.randomUUID();
    private static final UUID SNAPSHOT = UUID.randomUUID();

    /**
     * A real overlay, not a mock — the ordering assertion is only meaningful if the thing that
     * mutates the JSON actually runs.
     */
    private final ApiContractOverlay overlay = new ApiContractOverlay();

    @BeforeEach
    void setUp() {
        service = new DocumentGenerationService(
                repository, archiveRepository, requirementServiceClient, diagramServiceClient,
                ragServiceClient, aiServiceClient, mergeEngine, storageService, ragIndexingService,
                versionServiceClient, validationService, mapper, apiContractDeriver, overlay,
                Runnable::run);   // inline, so generation completes within the test
    }

    private void stubHappyPath(String modelJson) throws Exception {
        when(requirementServiceClient.getPcsfStatus(PROJECT)).thenReturn(
                ApiResponse.<RequirementServiceClient.PcsfStatusPayload>builder().status(200)
                        .data(new RequirementServiceClient.PcsfStatusPayload("APPROVED", 1.0, 0)).build());
        when(diagramServiceClient.list(PROJECT)).thenReturn(
                ApiResponse.<DiagramServiceClient.DiagramListData>builder().status(200)
                        .data(new DiagramServiceClient.DiagramListData(List.of(
                                new DiagramServiceClient.DiagramItem(UUID.randomUUID(), "USE_CASE", "APPROVED"))))
                        .build());
        when(ragServiceClient.getContext(any(), any(), any())).thenReturn("context");
        when(aiServiceClient.infer(any())).thenReturn(new InferenceResponseDTO("m", modelJson));
        when(mergeEngine.merge(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});
        when(storageService.saveDocument(any(), any(), any())).thenReturn("uploads/doc.docx");
        when(repository.findByProjectIdAndType(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getDocumentId() == null) d.setDocumentId(DOC);
            return d;
        });
    }

    private List<Document> savedDocuments() {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void whatIsStoredIsWhatWasMerged() throws Exception {
        stubHappyPath("{\"fr\":[{\"id\":\"FR-01\",\"description\":\"Reserve stock\"}]}");

        service.startGeneration(PROJECT, List.of(DocumentType.SRS));

        Document stored = savedDocuments().stream()
                .filter(d -> d.getContentJson() != null).reduce((a, b) -> b).orElseThrow();
        JsonNode parsed = mapper.readTree(stored.getContentJson());
        assertThat(parsed.get("fr").get(0).get("id").asText()).isEqualTo("FR-01");

        // ...and it is the same node the merge engine was handed.
        ArgumentCaptor<JsonNode> merged = ArgumentCaptor.forClass(JsonNode.class);
        verify(mergeEngine).merge(any(), any(), merged.capture());
        assertThat(mapper.readTree(stored.getContentJson())).isEqualTo(merged.getValue());
    }

    @Test
    void theApiContractIsStoredAfterTheDerivedEndpointsReplaceTheModelsOwn() throws Exception {
        // Storing the pre-overlay JSON would mean the persisted contract and the rendered .docx
        // disagree — reintroducing exactly the document/code drift the overlay exists to remove.
        when(apiContractDeriver.derive(any())).thenReturn(List.of(
                new ApiContractDeriver.DerivedEndpoint("API-01", "PATCH",
                        "/api/v1/products/{productId}/archive", "archiveProduct", "Archive",
                        "Product Management", List.of("ADMINISTRATOR"), true, false, false)));
        when(requirementServiceClient.getPcsf(PROJECT)).thenReturn(
                ApiResponse.<afb.astyann.documentservice.dto.pcsf.PcsfView>builder()
                        .status(200).data(new afb.astyann.documentservice.dto.pcsf.PcsfView()).build());
        stubHappyPath("""
                {"endpoint":[{"apiCode":"API-99","method":"DELETE","path":"/api/v1/products/{id}",
                 "description":"Invented by the model"}]}""");

        service.startGeneration(PROJECT, List.of(DocumentType.API_CONTRACT));

        Document stored = savedDocuments().stream()
                .filter(d -> d.getContentJson() != null).reduce((a, b) -> b).orElseThrow();
        assertThat(stored.getContentJson())
                .contains("/api/v1/products/{productId}/archive")
                .doesNotContain("DELETE");
    }

    @Test
    void aGenerationThatCannotSerialiseTheContentStillProducesTheDocument() throws Exception {
        // The .docx is what the user asked for. Losing the structured copy degrades a downstream
        // consumer; it must not turn a successful generation into a FAILED one.
        ObjectMapper throwingOnWrite = org.mockito.Mockito.spy(new ObjectMapper());
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(throwingOnWrite).writeValueAsString(any());
        service = new DocumentGenerationService(
                repository, archiveRepository, requirementServiceClient, diagramServiceClient,
                ragServiceClient, aiServiceClient, mergeEngine, storageService, ragIndexingService,
                versionServiceClient, validationService, throwingOnWrite, apiContractDeriver,
                overlay, Runnable::run);
        stubHappyPath("{\"fr\":[{\"id\":\"FR-01\"}]}");

        service.startGeneration(PROJECT, List.of(DocumentType.SRS));

        Document finalState = savedDocuments().get(savedDocuments().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.PENDING_APPROVAL);
        assertThat(finalState.getPath()).isEqualTo("uploads/doc.docx");
        assertThat(finalState.getContentJson()).isNull();
    }

    @Test
    void aRollbackRestoresTheContentAlongsideTheFile() {
        // Restoring only the file would leave the live JSON describing the version the rollback
        // exists to undo.
        Document live = Document.builder()
                .documentId(DOC).projectId(PROJECT).type(DocumentType.FUNCTIONAL_ANALYSIS)
                .status(DocumentStatus.APPROVED).version(2)
                .path("uploads/v2.docx").contentJson("{\"fr\":[{\"id\":\"FR-NEW\"}]}")
                .snapshotId(UUID.randomUUID()).build();
        when(repository.findById(DOC)).thenReturn(Optional.of(live));
        when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(archiveRepository.findBySnapshotIdAndDocumentId(SNAPSHOT, DOC))
                .thenReturn(Optional.of(DocumentVersionArchive.builder()
                        .snapshotId(SNAPSHOT).projectId(PROJECT).documentId(DOC)
                        .filePath("uploads/v1.docx").pageCount(4)
                        .contentJson("{\"fr\":[{\"id\":\"FR-OLD\"}]}").build()));

        Document restored = service.activateVersion(PROJECT, DOC, SNAPSHOT);

        assertThat(restored.getPath()).isEqualTo("uploads/v1.docx");
        assertThat(restored.getContentJson()).contains("FR-OLD").doesNotContain("FR-NEW");
        assertThat(restored.getSnapshotId()).isEqualTo(SNAPSHOT);
    }

    @Test
    void approvingArchivesTheContentAlongsideTheFilePath() {
        Document approved = Document.builder()
                .documentId(DOC).projectId(PROJECT).type(DocumentType.FUNCTIONAL_ANALYSIS)
                .status(DocumentStatus.PENDING_APPROVAL).version(1)
                .path("uploads/v1.docx").pageCount(3)
                .contentJson("{\"fr\":[{\"id\":\"FR-01\"}]}").build();
        when(repository.findById(DOC)).thenReturn(Optional.of(approved));
        when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByProjectId(PROJECT)).thenReturn(List.of(approved));
        when(validationService.validate(any())).thenReturn(
                afb.astyann.documentservice.dto.ValidationReportDTO.builder().build());
        when(versionServiceClient.createSnapshot(any(), any())).thenReturn(
                ApiResponse.<VersionServiceClient.SnapshotDTO>builder().status(200)
                        .data(new VersionServiceClient.SnapshotDTO(SNAPSHOT, UUID.randomUUID(), 1,
                                "DOCUMENT", true, DOC, "FUNCTIONAL_ANALYSIS")).build());

        service.approveDocument(PROJECT, DOC, "looks right");

        ArgumentCaptor<DocumentVersionArchive> archive =
                ArgumentCaptor.forClass(DocumentVersionArchive.class);
        verify(archiveRepository).save(archive.capture());
        assertThat(archive.getValue().getContentJson()).contains("FR-01");
        assertThat(archive.getValue().getFilePath()).isEqualTo("uploads/v1.docx");
    }
}
