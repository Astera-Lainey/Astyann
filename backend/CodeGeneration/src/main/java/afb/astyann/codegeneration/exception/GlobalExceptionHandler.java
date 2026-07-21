package afb.astyann.codegeneration.exception;

import afb.astyann.codegeneration.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PcsfNotApprovedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePcsfNotApproved(PcsfNotApprovedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.<Void>builder().status(422).message(ex.getMessage()).build());
    }

    @ExceptionHandler(DocumentsNotApprovedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentsNotApproved(DocumentsNotApprovedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder().status(409).message(ex.getMessage()).build());
    }

    @ExceptionHandler(CodeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(CodeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().status(404).message(ex.getMessage()).build());
    }

    @ExceptionHandler(CodeVersionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionNotFound(CodeVersionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().status(404).message(ex.getMessage()).build());
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownstream(DownstreamServiceException ex) {
        log.error("Downstream service failure", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Void>builder().status(503).message(ex.getMessage()).build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder().status(409).message(ex.getMessage()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder().status(500).message("Internal server error").build());
    }
}
