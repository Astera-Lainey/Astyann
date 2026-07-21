package afb.astyann.codegeneration.exception;

import java.util.UUID;

public class CodeVersionNotFoundException extends RuntimeException {
    public CodeVersionNotFoundException(UUID codeId, UUID snapshotId) {
        super("No archived version for code " + codeId + " with snapshot " + snapshotId + ".");
    }
}
