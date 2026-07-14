package afb.astyann.versionservice.exception;

import java.util.UUID;

public class TimelineNotFoundException extends RuntimeException {
    public TimelineNotFoundException(UUID projectId) {
        super("No timeline found for project " + projectId);
    }
}
