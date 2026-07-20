package afb.astyann.documentservice.service.schema;

import java.util.List;

/**
 * A contiguous span of paragraphs (e.g. a heading plus a couple of label lines) describing one
 * item, spread across multiple "${docxPrefix.field}" paragraphs rather than a single header
 * paragraph. The whole span is cloned once per item in the JSON array at jsonKey.
 */
public record ParagraphBlock(String docxPrefix, String jsonKey, List<String> fields) {
}
