package afb.astyann.documentservice.service.schema;

import afb.astyann.documentservice.domain.DocumentType;

import java.util.List;

public record DocumentSchema(DocumentType type, String templateResource, String promptHint,
                              List<ScalarField> scalars, List<RepeatingGroup> groups,
                              List<NestedGroupBlock> nestedBlocks) {
}
