package afb.astyann.documentservice.dto;

import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * A document's structured content — the JSON its {@code .docx} was merged from.
 *
 * <p>{@code content} is a {@code Map}, not a {@code JsonNode} and not a String. A String would hand
 * every consumer an escaped blob to parse a second time. A {@code JsonNode} looks like the natural
 * choice and is a trap here: this service's own {@code ObjectMapper} is Jackson 2, but Spring Boot 4
 * serialises HTTP with Jackson 3 ({@code tools.jackson}), which has no serialiser for a Jackson 2
 * {@code JsonNode} and silently emits its bean properties instead —
 * {@code {"array":false,"nodeType":"OBJECT",...}} in place of the document. A {@code Map} is the one
 * shape both Jackson generations handle identically, in both directions.
 *
 * <p>{@code snapshotId} identifies the approved version this content belongs to, so a consumer can
 * tell which version it is reading and cache against it.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentContentDTO {
    private UUID documentId;
    private DocumentType type;
    private DocumentStatus status;
    private Integer version;
    private UUID snapshotId;
    private Map<String, Object> content;
}
