package afb.astyann.diagramgeneratorservice.exception;

public class InvalidRenderFormatException extends RuntimeException {
    public InvalidRenderFormatException(String format) {
        super("Invalid render format: " + format + " (expected PNG or SVG)");
    }
}
