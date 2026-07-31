package afb.astyann.codegeneration.domain.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class FrontendColumn {
    private String label;
    private String fieldName;

    /**
     * True when this column holds a state-machine status and should render as a coloured badge
     * rather than raw text. Only set when the PCSF declares a status machine for the entity, so the
     * set of possible values — and therefore the colour mapping — is actually known.
     */
    private boolean badge;

    /**
     * TS object literal mapping each status value to a badge variant, e.g.
     * {@code { 'ACTIVE': 'success', 'ARCHIVED': 'danger' }}. Interpolate with a triple-mustache.
     */
    private String variantsExpression;
}
