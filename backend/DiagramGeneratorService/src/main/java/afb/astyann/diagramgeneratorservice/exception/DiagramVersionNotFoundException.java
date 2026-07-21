package afb.astyann.diagramgeneratorservice.exception;

import java.util.UUID;

public class DiagramVersionNotFoundException extends RuntimeException {
    public DiagramVersionNotFoundException(UUID diagramId, UUID snapshotId) {
        super("No archived version " + snapshotId + " found for diagram " + diagramId);
    }
}
