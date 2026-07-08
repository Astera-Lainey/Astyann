package afb.astyann.versionservice.exception;

import java.util.UUID;

public class SnapshotNotFoundException extends RuntimeException {
    public SnapshotNotFoundException(UUID snapId) {
        super("Snapshot not found: " + snapId);
    }
}
