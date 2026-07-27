package afb.astyann.codegeneration.domain.logic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Structured JSON reply expected from the AI when fixing a compile error.
 * {@code fixedSource} is the corrected file; {@code additionalFiles} lets the AI create
 * missing types (DTOs, enums, exceptions) that {@code cannot find symbol} errors require.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiCompileFixResponse {

    /** Full corrected source of the file that failed to compile. */
    private String fixedSource;

    /**
     * New supporting files. Keys are paths relative to {@code src/main/java/}, e.g.
     * {@code "com/afriland/inventorymanagement/dto/GoodsReceivedDto.java"}.
     */
    @Builder.Default
    private Map<String, String> additionalFiles = new HashMap<>();
}
