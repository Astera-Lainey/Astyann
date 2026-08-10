package afb.astyann.codegeneration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One approved document's structured content, as returned by DocumentService.
 *
 * <p>{@code content} is the JSON the document's {@code .docx} was merged from — the specification
 * as data. {@code snapshotId} identifies the approved version it came from, which is what scopes
 * the supplementary RAG lookup to the same version rather than to every document ever indexed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentContentDTO {
    private UUID documentId;
    private String type;
    private String status;
    private Integer version;
    private UUID snapshotId;
    private JsonNode content;
}
