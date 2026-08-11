package afb.astyann.documentservice.dto;

import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serialises the DTO with the mapper that actually puts it on the wire.
 *
 * <p>Spring Boot 4 serialises HTTP with Jackson 3 ({@code tools.jackson}), while this service's own
 * {@code ObjectMapper} bean is Jackson 2. A field typed as a Jackson 2 {@code JsonNode} therefore
 * looked correct in every unit test — they call the service directly and never cross the HTTP
 * boundary — while the endpoint returned the node's bean properties instead of the document:
 *
 * <pre>
 * "content": {"array":false,"nodeType":"OBJECT","empty":false,"valueNode":false, ...}
 * </pre>
 *
 * <p>The consumer then failed with {@code Type definition error: [simple type, class
 * com.fasterxml.jackson.databind.JsonNode]}. These tests exist because the boundary is where the
 * bug lived, so the boundary is what has to be asserted.
 */
class DocumentContentWireFormatTest {

    /** The mapper Spring Boot 4 uses for HTTP, not the Jackson 2 bean this service injects. */
    private final tools.jackson.databind.ObjectMapper httpMapper = new tools.jackson.databind.ObjectMapper();

    private static DocumentContentDTO dto(Map<String, Object> content) {
        return DocumentContentDTO.builder()
                .documentId(UUID.randomUUID())
                .type(DocumentType.FUNCTIONAL_ANALYSIS)
                .status(DocumentStatus.APPROVED)
                .version(2)
                .snapshotId(UUID.randomUUID())
                .content(content)
                .build();
    }

    @Test
    void contentSerialisesAsTheDocumentNotAsAWrapperObjectsProperties() {
        String json = httpMapper.writeValueAsString(dto(Map.of(
                "introduction", "Scope of the system",
                "fr", List.of(Map.of("id", "FR-01", "description", "Reserve stock")))));

        assertThat(json)
                .contains("\"introduction\":\"Scope of the system\"")
                .contains("\"id\":\"FR-01\"")
                // The tell-tale properties of a JsonNode serialised as a bean.
                .doesNotContain("nodeType")
                .doesNotContain("valueNode")
                .doesNotContain("containerNode");
    }

    @Test
    void nestedArraysAndObjectsSurviveTheRoundTrip() {
        Map<String, Object> content = Map.of(
                "fr", List.of(
                        Map.of("id", "FR-01", "description", "First"),
                        Map.of("id", "FR-02", "description", "Second")),
                "coverageStats", Map.of("totalEndpoints", 6));

        String json = httpMapper.writeValueAsString(dto(content));
        DocumentContentDTO back = httpMapper.readValue(json, DocumentContentDTO.class);

        assertThat(back.getContent()).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frs = (List<Map<String, Object>>) back.getContent().get("fr");
        assertThat(frs).hasSize(2);
        assertThat(frs.get(1).get("id")).isEqualTo("FR-02");
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) back.getContent().get("coverageStats");
        assertThat(stats.get("totalEndpoints")).isEqualTo(6);
    }

    @Test
    void theEnvelopeFieldsSurviveTooSoAConsumerCanIdentifyTheVersion() {
        DocumentContentDTO original = dto(Map.of("introduction", "x"));

        DocumentContentDTO back = httpMapper.readValue(
                httpMapper.writeValueAsString(original), DocumentContentDTO.class);

        assertThat(back.getDocumentId()).isEqualTo(original.getDocumentId());
        assertThat(back.getSnapshotId()).isEqualTo(original.getSnapshotId());
        assertThat(back.getType()).isEqualTo(DocumentType.FUNCTIONAL_ANALYSIS);
        assertThat(back.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(back.getVersion()).isEqualTo(2);
    }
}
