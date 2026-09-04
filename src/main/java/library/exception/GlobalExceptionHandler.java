package library.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex.getMessage()));
    }

    @ExceptionHandler(BookNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(BookNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateLoanException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateLoan(DuplicateLoanException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage()));
    }

    /**
     * Backstop for the database constraints that guard the same rules the
     * services check up front - most notably the unique (book_id, borrower_id)
     * on loans. Reaching here means a concurrent request won a race the service
     * layer expected to have serialised, which is a conflict, not a server
     * fault. The exception's own message is not echoed back: it carries SQL and
     * constraint names that callers have no business seeing.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("Request conflicts with the current state of the resource"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(message));
    }

    private Map<String, Object> body(String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "message", message
        );
    }
}
