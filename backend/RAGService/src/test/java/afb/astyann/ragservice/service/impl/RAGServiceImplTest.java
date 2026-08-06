package afb.astyann.ragservice.service.impl;

import afb.astyann.ragservice.dto.BatchIndexRequestDTO;
import afb.astyann.ragservice.dto.IndexRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The vector store only ever appends, so re-indexing a source used to leave its previous text in
 * place alongside the new text — both competing in similarity search, with nothing marking which
 * was current. These tests pin the replace-then-add behaviour that fixes it.
 */
@ExtendWith(MockitoExtension.class)
class RAGServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private RAGServiceImpl service;

    private static final UUID PROJECT = UUID.randomUUID();

    private static IndexRequestDTO item(UUID sourceId, UUID snapshotId, String content) {
        return IndexRequestDTO.builder()
                .projectId(PROJECT).sourceType("DOCUMENT").sourceId(sourceId)
                .snapshotId(snapshotId).content(content)
                .metadata(Map.of("documentType", "SFD"))
                .build();
    }

    private static List<Document> stored(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> new Document(id, "previously indexed text", Map.of()))
                .toList();
    }

    @Test
    void indexDocumentRemovesThePreviousVersionBeforeAddingTheNewOne() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(stored("old-1", "old-2"));

        service.indexDocument(item(UUID.randomUUID(), UUID.randomUUID(), "the approved text"));

        ArgumentCaptor<List<String>> deleted = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).delete(deleted.capture());
        assertThat(deleted.getValue()).containsExactly("old-1", "old-2");
        verify(vectorStore).add(anyList());
    }

    @Test
    void indexBatchDeletesOncePerSourceNotOncePerChunk() {
        // A batch normally carries many chunks of the same source. Deleting per chunk would wipe
        // the chunks written moments earlier in the same batch.
        UUID sourceA = UUID.randomUUID();
        UUID sourceB = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(stored("old-1"));

        service.indexBatch(BatchIndexRequestDTO.builder()
                .projectId(PROJECT).sourceType("REQUIREMENT")
                .items(List.of(
                        item(sourceA, null, "chunk one"),
                        item(sourceA, null, "chunk two"),
                        item(sourceB, null, "another source")))
                .build());

        verify(vectorStore, times(2)).delete(anyList());
        // ...and everything is written in a single add, after the deletes.
        verify(vectorStore).add(anyList());
    }

    @Test
    void indexingStillWorksWhenTheCallerSendsNoSourceId() {
        // Nothing identifies what to replace, so adding without deleting is the only safe choice.
        service.indexDocument(IndexRequestDTO.builder()
                .projectId(PROJECT).sourceType("DOCUMENT").content("text with no source id").build());

        verify(vectorStore, never()).delete(anyList());
        verify(vectorStore).add(anyList());
    }

    @Test
    void stampsSourceAndSnapshotMetadataOntoEveryChunk() {
        UUID sourceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.indexDocument(item(sourceId, snapshotId, "the approved text"));

        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(added.capture());
        assertThat(added.getValue()).isNotEmpty();
        Map<String, Object> meta = added.getValue().get(0).getMetadata();
        assertThat(meta).containsEntry("projectId", PROJECT.toString())
                .containsEntry("sourceType", "DOCUMENT")
                .containsEntry("sourceId", sourceId.toString())
                // Traces a retrieved passage back to the approved version that produced it.
                .containsEntry("snapshotId", snapshotId.toString())
                .containsEntry("documentType", "SFD");
    }

    @Test
    void recordsAnEmptySnapshotIdRatherThanOmittingTheKey() {
        // Approval can outrun snapshot creation; the chunk is still indexed, just unversioned.
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.indexDocument(item(UUID.randomUUID(), null, "approved before the snapshot landed"));

        ArgumentCaptor<List<Document>> added = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(added.capture());
        assertThat(added.getValue().get(0).getMetadata()).containsEntry("snapshotId", "");
    }

    @Test
    void retrievalExposesTheSourceAndSnapshotBehindEveryChunk() {
        // Without this a caller cannot distinguish current text from stale text in a result.
        UUID sourceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("c1", "approved text", Map.of(
                        "sourceType", "DOCUMENT",
                        "sourceId", sourceId.toString(),
                        "snapshotId", snapshotId.toString()))));

        var response = service.retrieveContext(PROJECT, "how is stock reserved", 5, null);

        assertThat(response.getMetadata()).hasSize(1);
        assertThat(response.getMetadata().get(0))
                .containsEntry("sourceType", "DOCUMENT")
                .containsEntry("sourceId", sourceId.toString())
                .containsEntry("snapshotId", snapshotId.toString());
        // Positional alignment with chunks is what makes the metadata usable.
        assertThat(response.getChunks()).hasSize(1).containsExactly("approved text");
    }

    @Test
    void deleteBySourceReportsHowManyChunksItRemoved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(stored("a", "b", "c"));

        assertThat(service.deleteBySource(PROJECT, "DOCUMENT", UUID.randomUUID())).isEqualTo(3);
        verify(vectorStore).delete(anyList());
    }

    @Test
    void deleteBySourceIsANoOpWhenNothingIsStored() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(service.deleteBySource(PROJECT, "DOCUMENT", UUID.randomUUID())).isZero();
        verify(vectorStore, never()).delete(anyList());
    }

    @Test
    void deleteBySourceRefusesToRunWithoutBothIdentifiers() {
        // A null sourceId would otherwise widen the filter to the whole project and delete far
        // more than intended.
        assertThat(service.deleteBySource(PROJECT, "DOCUMENT", null)).isZero();
        assertThat(service.deleteBySource(null, "DOCUMENT", UUID.randomUUID())).isZero();
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        verify(vectorStore, never()).delete(anyList());
    }
}
