package afb.astyann.requirementservice.service.pcsf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovers the usable prefix of a model response that stopped mid-JSON.
 *
 * <p>A generation that runs out of output budget ends wherever it happened to be — commonly inside
 * a string, which surfaces as {@code Unexpected end-of-input: was expecting closing quote}. The
 * inference pipeline used to hand that straight to {@code readTree} and let the exception abort
 * everything, so a response carrying twenty complete entities and one half-written attribute
 * contributed nothing at all.
 *
 * <p>The repair is deliberately conservative: it rewinds to the last point where a value had just
 * finished, discards the partial tail, and closes the containers that were still open. Nothing is
 * invented and no value is altered — the result is a strict prefix of what the model actually said,
 * made syntactically whole. A caller still sees fewer items than it asked for, which is the honest
 * outcome, and the log says how much was lost.
 *
 * <p><strong>Recovery is field-granular, not element-granular.</strong> The rewind stops at the
 * last completed <em>field</em>, so the object being written when the cut happened survives with
 * the fields it had already emitted and without the rest — {@code {"id":"entity_2"}} from an entity
 * whose name was half-written. That is deliberate: it also preserves top-level scalars such as
 * {@code apiConfig.versionPrefix}, which an element-granular rewind would throw away, and every
 * consumer of the PCSF already skips records with missing required values ({@code ProjectionBuilder}
 * drops entities with a blank name, {@code PcsfCrossReferenceReconciler} clears references that
 * resolve to nothing). A partially populated record is therefore inert downstream, whereas a
 * discarded {@code apiConfig} would not be.
 */
@Component
@Slf4j
public class TruncatedJsonRepair {

    /** A point where the text can be cut cleanly, plus the containers open at that point. */
    private record CutPoint(int index, List<Character> open) {}

    /**
     * Parses {@code raw}, repairing a truncated tail if needed.
     *
     * @return the parsed tree, or {@code null} when nothing usable could be recovered
     */
    public JsonNode parseTolerantly(ObjectMapper mapper, String raw, String context) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return mapper.readTree(raw);
        } catch (Exception first) {
            String repaired = repair(raw);
            if (repaired == null) {
                log.warn("{}: response was not valid JSON and no complete prefix could be recovered "
                         + "({} chars). Cause: {}", context, raw.length(), first.getMessage());
                return null;
            }
            try {
                JsonNode node = mapper.readTree(repaired);
                log.warn("{}: response was truncated at {} chars — recovered the first {} chars "
                         + "({}% ). The model most likely hit its output-token budget; anything after "
                         + "the cut is missing from this pass.",
                        context, raw.length(), repaired.length(),
                        Math.round(100.0 * repaired.length() / raw.length()));
                return node;
            } catch (Exception second) {
                log.warn("{}: response was truncated and the recovered prefix still did not parse: {}",
                        context, second.getMessage());
                return null;
            }
        }
    }

    /**
     * Rewinds to the last complete value and closes whatever containers remain open.
     *
     * @return repaired JSON, or {@code null} if there is no complete value to keep
     */
    String repair(String raw) {
        List<Character> stack = new ArrayList<>();
        CutPoint lastSafe = null;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> stack.add(c);
                case '}', ']' -> {
                    if (stack.isEmpty()) return null;   // more closers than openers: not recoverable
                    stack.remove(stack.size() - 1);
                    // A value just ended here, so everything up to and including it is keepable.
                    if (!stack.isEmpty()) lastSafe = new CutPoint(i + 1, new ArrayList<>(stack));
                }
                // A separator means the element before it is complete; cut before the comma so no
                // dangling separator is left behind.
                case ',' -> lastSafe = new CutPoint(i, new ArrayList<>(stack));
                default -> { /* whitespace, literals and numbers need no tracking */ }
            }
        }

        if (lastSafe == null) return null;

        StringBuilder sb = new StringBuilder(raw.substring(0, lastSafe.index()));
        for (int i = lastSafe.open().size() - 1; i >= 0; i--) {
            sb.append(lastSafe.open().get(i) == '{' ? '}' : ']');
        }
        return sb.toString();
    }
}
