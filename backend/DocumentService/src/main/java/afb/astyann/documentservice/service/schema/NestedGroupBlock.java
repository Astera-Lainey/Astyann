package afb.astyann.documentservice.service.schema;

import java.util.List;

/**
 * A "header paragraph + table" pair repeated once per outer JSON array item, with the inner
 * table itself repeating once per item of a nested array field on that outer item
 * (outerJsonKey[].innerJsonField[]). Only data-dictionary needs this (tables[].columns[]).
 */
public record NestedGroupBlock(String headerDocxPrefix, String rowDocxPrefix,
                                String outerJsonKey, String innerJsonField,
                                List<String> rowFields) {
}
