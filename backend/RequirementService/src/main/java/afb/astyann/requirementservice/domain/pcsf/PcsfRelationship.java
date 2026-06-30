package afb.astyann.requirementservice.domain.pcsf;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PcsfRelationship {
    private String id;

    @JsonAlias({"fromEntity", "source", "from", "entity1", "sourceEntityId"})
    private String fromEntityId;

    @JsonAlias({"toEntity", "target", "to", "entity2", "targetEntityId"})
    private String toEntityId;

    @JsonAlias({"type", "relationType", "relationship_type", "multiplicity"})
    private FieldValue<String> cardinality;

    private FieldValue<String> optionality;
    private String owningEntityId;
    private String joinColumnName;
    private String joinTableName;

    @JsonAlias({"name", "title", "relationshipLabel", "description"})
    private FieldValue<String> label;
}
