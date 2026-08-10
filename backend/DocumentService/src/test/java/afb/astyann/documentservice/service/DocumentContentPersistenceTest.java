package afb.astyann.documentservice.service;

import afb.astyann.documentservice.client.RequirementServiceClient;
import afb.astyann.documentservice.client.VersionServiceClient;
import afb.astyann.documentservice.domain.Document;
import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import afb.astyann.documentservice.domain.DocumentVersionArchive;
import afb.astyann.documentservice.dto.DocumentContentDTO;
import afb.astyann.documentservice.exception.DocumentContentNotAvailableException;
import afb.astyann.documentservice.exception.DocumentNotFoundException;
import afb.astyann.documentservice.exception.DocumentVersionNotFoundException;
import afb.astyann.documentservice.repository.DocumentRepository;
import afb.astyann.documentservice.repository.DocumentVersionArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Documents are schema-driven: a model returns JSON, which is merged into a Word template. Only
 * the merged .docx used to be kept, so the machine-readable form was destroyed and then partly
 * reconstructed downstream by extracting prose back out of Word. These cover keeping it, versioning
 * it with the .docx it produced, and reading it back.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentContentPersistenceTest {

    @Mock private DocumentRepository repository;
    @Mock private DocumentVersionArchiveRepository archiveRepository;
    @Mock private RequirementServiceClient requirementServiceClient;
    @Mock private VersionServiceClient versionServiceClient;
    @Mock private DocxMergeEngine mergeEngine;
    @Mock private StorageService storageService;
    @Mock private DocumentRagIndexingService ragIndexingService;
    @Mock private DocumentValidationService validationService;
    @Mock private ApiContractDeriver apiContractDeriver;
    @Mock private ApiContractOverlay apiContractOverlay;
    @Mock private Executor documentExecutor;

    private DocumentGenerationService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID DOC = UUID.randomUUID();
    private static final UUID SNAPSHOT = UUID.randomUUID();

    private static final String CONTENT = """
            {"fr":[{"id":"FR-01","description":"Reserve stock on order"}],"introduction":"x"}""";

    @BeforeEach
    void setUp() {
        service = new DocumentGenerationService(
                repository, archiveRepository, requirementServiceClient,
                null, null, null, mergeEngine, storageService, ragIndexingService,
                versionServiceClient, validationService, mapper,
                apiContractDeriver, apiContractOverlay, documentExecutor);
    }

    private static Document doc(String contentJson, DocumentStatus status) {
        return Document.builder()
                .documentId(DOC).projectId(PROJECT).type(DocumentType.FUNCTIONAL_ANALYSIS)
                .status(status).version(2).snapshotId(SNAPSHOT)
                .path("uploads/doc.docx").contentJson(contentJson)
                .build();
    }

    @Test
    void returnsTheStoredJsonAsRealJsonNotAnEscapedString() throws Exception {
        when(repository.findById(DOC)).thenReturn(Optional.of(doc(CONTENT, DocumentStatus.APPROVED)));

        DocumentContentDTO dto = service.getContent(PROJECT, DOC);

        // A String field would force every consumer to parse a second time.
        assertThat(dto.getContent().isObject()).isTrue();
        assertThat(dto.getContent().get("fr").get(0).get("id").asText()).isEqualTo("FR-01");
        assertThat(dto.getSnapshotId()).isEqualTo(SNAPSHOT);
        assertThat(dto.getType()).isEqualTo(DocumentType.FUNCTIONAL_ANALYSIS);
        assertThat(dto.getVersion()).isEqualTo(2);
    }

    @Test
    void aDocumentGeneratedBeforeContentWasStoredIsDistinguishableFromAMissingOne() {
        // 409 vs 404 matters: one means "regenerate it", the other means "wrong id". A caller
        // cannot give a useful error without being able to tell them apart.
        when(repository.findById(DOC)).thenReturn(Optional.of(doc(null, DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.getContent(PROJECT, DOC))
                .isInstanceOf(DocumentContentNotAvailableException.class)
                .hasMessageContaining("regenerate");
    }

    @Test
    void blankStoredContentIsTreatedTheSameAsAbsent() {
        when(repository.findById(DOC)).thenReturn(Optional.of(doc("   ", DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.getContent(PROJECT, DOC))
                .isInstanceOf(DocumentContentNotAvailableException.class);
    }

    @Test
    void aDocumentBelongingToAnotherProjectIsNotReadable() {
        when(repository.findById(DOC)).thenReturn(Optional.of(doc(CONTENT, DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.getContent(UUID.randomUUID(), DOC))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void versionContentComesFromTheArchiveNotFromWhateverIsCurrentlyLive() {
        // The point of the endpoint: "show me v1" must keep meaning v1 after a rollback to v2.
        String archived = "{\"fr\":[{\"id\":\"FR-OLD\"}]}";
        when(repository.findById(DOC)).thenReturn(Optional.of(doc(CONTENT, DocumentStatus.APPROVED)));
        when(archiveRepository.findBySnapshotIdAndDocumentId(SNAPSHOT, DOC))
                .thenReturn(Optional.of(DocumentVersionArchive.builder()
                        .snapshotId(SNAPSHOT).projectId(PROJECT).documentId(DOC)
                        .filePath("uploads/v1.docx").contentJson(archived).build()));

        DocumentContentDTO dto = service.getVersionContent(PROJECT, DOC, SNAPSHOT);

        assertThat(dto.getContent().get("fr").get(0).get("id").asText()).isEqualTo("FR-OLD");
    }

    @Test
    void anUnknownSnapshotIsReportedAsAMissingVersion() {
        when(repository.findById(DOC)).thenReturn(Optional.of(doc(CONTENT, DocumentStatus.APPROVED)));
        when(archiveRepository.findBySnapshotIdAndDocumentId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVersionContent(PROJECT, DOC, UUID.randomUUID()))
                .isInstanceOf(DocumentVersionNotFoundException.class);
    }

    @Test
    void theTypeLookupReturnsOnlyApprovedContent() {
        // A generator must never build from text nobody accepted.
        when(repository.findByProjectIdAndType(PROJECT, DocumentType.FUNCTIONAL_ANALYSIS))
                .thenReturn(Optional.of(doc(CONTENT, DocumentStatus.PENDING_APPROVAL)));

        assertThatThrownBy(() -> service.getApprovedContent(PROJECT, DocumentType.FUNCTIONAL_ANALYSIS))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void theTypeLookupSucceedsOnceApproved() {
        when(repository.findByProjectIdAndType(PROJECT, DocumentType.FUNCTIONAL_ANALYSIS))
                .thenReturn(Optional.of(doc(CONTENT, DocumentStatus.APPROVED)));

        DocumentContentDTO dto = service.getApprovedContent(PROJECT, DocumentType.FUNCTIONAL_ANALYSIS);

        assertThat(dto.getContent().get("fr")).hasSize(1);
        assertThat(dto.getStatus()).isEqualTo(DocumentStatus.APPROVED);
    }

    @Test
    void unparseableStoredContentIsReportedRatherThanHandedBackBroken() {
        when(repository.findById(DOC)).thenReturn(Optional.of(doc("{not json", DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.getContent(PROJECT, DOC))
                .isInstanceOf(DocumentContentNotAvailableException.class);
    }
}
