package afb.astyann.diagramgeneratorservice.exception;

import java.util.UUID;

public class DiagramNotFoundException extends RuntimeException {
    public DiagramNotFoundException(UUID projectId, UUID diagramId) {
        super("Diagram " + diagramId + " not found for project " + projectId);
    }

    public DiagramNotFoundException(UUID projectId) {
        super("No diagrams found for project " + projectId);
    }
}
