package afb.astyann.documentservice.dto;

import afb.astyann.documentservice.domain.DocumentStatus;
import afb.astyann.documentservice.domain.DocumentType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A document's structured content — the JSON its {@code .docx} was merged from.
 *
 * <p>{@code content} is a {@link JsonNode} rather than a String so it nests inside the response
 * envelope as real JSON; returning it as a string would hand every consumer an escaped blob to
 * parse a second time.
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
    private JsonNode content;
}
