package afb.astyann.documentservice.exception;

public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public DownstreamServiceException(String message) {
        super(message);
    }
}
