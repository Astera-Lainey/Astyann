package afb.astyann.codegeneration.exception;

import java.util.UUID;

public class CodeNotFoundException extends RuntimeException {
    public CodeNotFoundException(UUID projectId) {
        super("No generated code found for project " + projectId + ".");
    }
}
