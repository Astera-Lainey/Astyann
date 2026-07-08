package afb.astyann.versionservice.exception;

import afb.astyann.versionservice.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TimelineNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimelineNotFound(TimelineNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().status(404).message(ex.getMessage()).build());
    }

    @ExceptionHandler(SnapshotNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSnapshotNotFound(SnapshotNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().status(404).message(ex.getMessage()).build());
    }

    @ExceptionHandler(InvalidSnapshotRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(InvalidSnapshotRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder().status(400).message(ex.getMessage()).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Validation failed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder().status(400).message(message).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder().status(500).message("Internal server error").build());
    }
}
