package afb.astyann.codegeneration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * One approved document's structured content, as returned by DocumentService.
 *
 * <p>{@code content} is the JSON the document's {@code .docx} was merged from — the specification
 * as data. {@code snapshotId} identifies the approved version it came from, which is what scopes
 * the supplementary RAG lookup to the same version rather than to every document ever indexed.
 *
 * <p>A {@code Map} rather than a {@code JsonNode}: Spring Boot 4 deserialises HTTP with Jackson 3
 * ({@code tools.jackson}), which cannot construct a Jackson 2 {@code JsonNode} and fails with
 * "Type definition error". This must stay in step with DocumentService's DTO of the same name.
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
    private Map<String, Object> content;
}
