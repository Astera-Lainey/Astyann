package afb.astyann.documentservice.service.schema;

import java.util.List;

/**
 * A table row templated with "${docxPrefix.field}" placeholders, repeated once per item in
 * the JSON array at jsonKey.
 */
public record RepeatingGroup(String docxPrefix, String jsonKey, List<String> fields) {
}
