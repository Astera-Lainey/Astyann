package afb.astyann.documentservice.service.schema;

import java.util.List;

/**
 * A table where a single item's fields are laid out as rows (one row per field, e.g. a
 * label/value pair table describing one use case or one API endpoint) rather than as columns
 * of a repeatable row. The entire table is cloned once per item in the JSON array at jsonKey,
 * each clone's rows filled from that item's fields.
 */
public record VerticalBlock(String docxPrefix, String jsonKey, List<String> fields) {
}