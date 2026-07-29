package afb.astyann.codegeneration.service.logic;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Salvages field values from *nearly*-JSON model output.
 *
 * <p>Models are asked to return a JSON object whose values are entire Java source files. Escaping
 * thousands of characters of code without a single mistake is exactly what they are worst at: one
 * stray quote or backslash deep inside {@code serviceImpl} makes the whole document structurally
 * invalid, and a strict (or even lenient) parser then yields nothing — throwing away a response
 * that is 99% usable.
 *
 * <p>This extractor never parses the document as a whole. It locates {@code "field":} and reads the
 * following string literal character by character, honouring backslash escapes and stopping at the
 * first unescaped quote. Damage elsewhere in the document cannot affect a field that reads cleanly,
 * and a value truncated at end-of-input is returned as far as it got.
 *
 * <p>Anything recovered still has to survive {@code FilePatcher.replaceEntireFile}, which refuses
 * source that does not parse as Java — so a mangled salvage is dropped rather than written.
 */
@Component
public class AiJsonExtractor {

    /** A string literal read out of the raw text, plus the index just past its closing quote. */
    private record Scanned(String value, int end) {}

    /**
     * Returns the string value of {@code "field": "..."}, or empty if the key is absent or is not
     * followed by a string literal.
     */
    public Optional<String> stringField(String raw, String field) {
        if (raw == null || field == null) return Optional.empty();
        int key = indexOfKey(raw, field, 0);
        if (key < 0) return Optional.empty();
        int colon = raw.indexOf(':', key);
        if (colon < 0) return Optional.empty();
        int q = nextNonWhitespace(raw, colon + 1);
        if (q < 0 || raw.charAt(q) != '"') return Optional.empty();
        Scanned scanned = readString(raw, q);
        return scanned == null || scanned.value().isBlank()
                ? Optional.empty() : Optional.of(scanned.value());
    }

    /**
     * Returns the {@code "field": { "k": "v", ... }} object as a map. Entries whose value is not a
     * string are skipped. Reading stops at the object's closing brace or at end-of-input.
     */
    public Map<String, String> stringMapField(String raw, String field) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || field == null) return out;
        int key = indexOfKey(raw, field, 0);
        if (key < 0) return out;
        int colon = raw.indexOf(':', key);
        if (colon < 0) return out;
        int brace = nextNonWhitespace(raw, colon + 1);
        if (brace < 0 || raw.charAt(brace) != '{') return out;

        int i = brace + 1;
        while (i < raw.length()) {
            int k = nextNonWhitespace(raw, i);
            if (k < 0 || raw.charAt(k) == '}') break;
            if (raw.charAt(k) == ',') { i = k + 1; continue; }
            if (raw.charAt(k) != '"') break;              // unexpected shape — stop cleanly

            Scanned entryKey = readString(raw, k);
            if (entryKey == null) break;
            int c = nextNonWhitespace(raw, entryKey.end());
            if (c < 0 || raw.charAt(c) != ':') break;
            int v = nextNonWhitespace(raw, c + 1);
            if (v < 0) break;
            if (raw.charAt(v) != '"') {                    // non-string value — skip this entry
                i = v + 1;
                continue;
            }
            Scanned entryValue = readString(raw, v);
            if (entryValue == null) break;
            if (!entryKey.value().isBlank() && !entryValue.value().isBlank()) {
                out.put(entryKey.value(), entryValue.value());
            }
            i = entryValue.end();
        }
        return out;
    }

    // ── internals ───────────────────────────────────────────────────────────

    /** Finds {@code "field"} followed by optional whitespace and a colon, from {@code from}. */
    private int indexOfKey(String raw, String field, int from) {
        String needle = '"' + field + '"';
        int i = raw.indexOf(needle, from);
        while (i >= 0) {
            int after = nextNonWhitespace(raw, i + needle.length());
            if (after >= 0 && raw.charAt(after) == ':') return i;
            i = raw.indexOf(needle, i + 1);
        }
        return -1;
    }

    private int nextNonWhitespace(String raw, int from) {
        for (int i = Math.max(0, from); i < raw.length(); i++) {
            if (!Character.isWhitespace(raw.charAt(i))) return i;
        }
        return -1;
    }

    /**
     * Reads the JSON string literal starting at {@code openQuote}, decoding escapes. An unknown
     * escape keeps the escaped character verbatim (models emit {@code \d} inside regexes). If the
     * closing quote is missing, everything up to end-of-input is returned.
     */
    private Scanned readString(String raw, int openQuote) {
        if (openQuote < 0 || openQuote >= raw.length() || raw.charAt(openQuote) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int i = openQuote + 1; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"') return new Scanned(sb.toString(), i + 1);
            if (ch != '\\') { sb.append(ch); continue; }

            if (++i >= raw.length()) break;
            char esc = raw.charAt(i);
            switch (esc) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append("\\u");
                        }
                    } else {
                        sb.append("\\u");
                    }
                }
                // Not a JSON escape (e.g. \d in a regex) — keep the character itself.
                default -> sb.append(esc);
            }
        }
        return new Scanned(sb.toString(), raw.length()); // unterminated — salvage what we have
    }
}
