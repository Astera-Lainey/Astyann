package afb.astyann.codegeneration.domain.logic;

import java.nio.file.Path;

/** A single compiler error parsed out of a Maven build log. */
public record CompileError(Path file, int line, int column, String message) {
    public String location() {
        return file.getFileName() + ":" + line + ":" + column;
    }
}
