package app.banking.transactionservice.exception;

/**
 * Raised when a saga step cannot be published to Kafka. The sender has already
 * been debited at that point, so the caller must be told the transfer was
 * aborted rather than receiving a success response.
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
