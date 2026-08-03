package afb.astyann.codegeneration.service.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal read/write helper for TypeScript files patched by the frontend compile-fix loop.
 * There is no JavaParser equivalent for TS, so writes are guarded only by a non-blank check and
 * code-fence stripping — best-effort, matching the tolerance of the backend path.
 */
@Service
@Slf4j
public class TsFilePatcher {

    public String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** @deprecated use {@link #writeIfSane(Path, String, String)} so the guard can compare sizes. */
    @Deprecated
    public boolean writeIfSane(Path file, String source) {
        return writeIfSane(file, source, null);
    }

    /**
     * Writes {@code source} (fences stripped) to {@code file} after sanity checks. There is no
     * TypeScript/HTML parser here — unlike the Java path — so these heuristics are the only thing
     * standing between a mangled model response and a destroyed source file.
     *
     * <p>Rejects output that is blank, that collapsed to a small fraction of the original (the
     * shape truncation and "explained instead of answered" responses take), or that swapped
     * language — a component class returned for a template, or markup returned for TypeScript.
     *
     * @param previous the file's current contents, or {@code null} to skip the size comparison
     */
    public boolean writeIfSane(Path file, String source, String previous) {
        String name = file.getFileName().toString();
        if (source == null || source.isBlank()) {
            log.warn("Refusing to write {} — AI returned nothing.", name);
            return false;
        }
        String cleaned = stripCodeFences(source);
        if (cleaned.isBlank()) {
            log.warn("Refusing to write {} — AI response was only a code fence.", name);
            return false;
        }
        if (previous != null && !previous.isBlank()
                && cleaned.length() < Math.max(40, previous.length() * MIN_RETAINED_FRACTION / 100)) {
            log.warn("Refusing to write {} — replacement is {} chars vs {} originally, which "
                    + "suggests a truncated or partial response.", name, cleaned.length(), previous.length());
            return false;
        }
        if (!languageLooksRight(name, cleaned)) {
            log.warn("Refusing to write {} — replacement does not look like the right language "
                    + "for this file.", name);
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, cleaned, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            log.warn("Could not write {}: {}", file, ex.getMessage());
            return false;
        }
    }

    /** A replacement must retain at least this percentage of the original file's length. */
    private static final int MIN_RETAINED_FRACTION = 40;

    /**
     * Cheap language check. An Angular template must not come back as a component class, and a
     * TypeScript file must not come back as bare markup.
     */
    private boolean languageLooksRight(String fileName, String content) {
        String head = content.stripLeading();
        if (fileName.endsWith(".html")) {
            return !head.startsWith("import ") && !head.startsWith("@Component");
        }
        if (fileName.endsWith(".ts")) {
            return !head.startsWith("<");
        }
        return true;
    }

    private String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
