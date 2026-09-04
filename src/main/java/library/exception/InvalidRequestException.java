package library.exception;

/**
 * A request that is well-formed and passes bean validation but is still
 * unusable - typically because a field cannot produce a meaningful generated
 * identifier. Maps to {@code 400 Bad Request}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
