package afb.astyann.ragservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetrievalResponseDTO {
    private String context;
    private List<String> chunks;
    private List<String> sources;
    private List<Double> scores;
    /**
     * Per-chunk metadata, positionally aligned with {@code chunks} — {@code sourceType},
     * {@code sourceId} and {@code snapshotId}. Without it a caller cannot tell which version of a
     * document a passage came from, which makes stale content indistinguishable from current.
     */
    private List<Map<String, Object>> metadata;
}
