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

    /**
     * Writes {@code source} (fences stripped) to {@code file} if it is non-blank. Returns whether
     * the file was written.
     */
    public boolean writeIfSane(Path file, String source) {
        if (source == null || source.isBlank()) {
            log.warn("Refusing to write {} — AI returned empty TypeScript.", file.getFileName());
            return false;
        }
        String cleaned = stripCodeFences(source);
        if (cleaned.isBlank()) return false;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, cleaned, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            log.warn("Could not write {}: {}", file, ex.getMessage());
            return false;
        }
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
