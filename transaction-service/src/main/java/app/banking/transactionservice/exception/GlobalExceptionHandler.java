package app.banking.transactionservice.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex, HttpServletRequest request) {

        log.warn("Transaction not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * A failed saga publish means the transfer was rolled back. Report it as
     * 503 so the caller knows to retry rather than assuming success.
     */
    @ExceptionHandler(EventPublishException.class)
    public ResponseEntity<ErrorResponse> handleEventPublish(
            EventPublishException ex, HttpServletRequest request) {

        log.error("Saga publish failed on {}", request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /**
     * Relay the status and message from account-service instead of collapsing
     * every upstream 4xx into a local 500. A missing account stays a 404, an
     * insufficient balance stays a 422.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(
            FeignException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.status()) != null
                ? HttpStatus.valueOf(ex.status())
                : HttpStatus.BAD_GATEWAY;

        String message = extractUpstreamMessage(ex);

        log.warn("Upstream call failed [{}] on {}: {}",
                status.value(), request.getRequestURI(), message);

        return build(status, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Request validation failed")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    /**
     * account-service returns an ErrorResponse body; pull its message out so the
     * real reason survives the hop. Falls back to the raw Feign message.
     */
    private String extractUpstreamMessage(FeignException ex) {
        String body = ex.contentUTF8();

        if (body != null && !body.isBlank()) {
            try {
                JsonNode message = objectMapper.readTree(body).get("message");
                if (message != null && !message.asText().isBlank()) {
                    return message.asText();
                }
            } catch (Exception parseFailure) {
                log.debug("Upstream error body was not JSON: {}", parseFailure.getMessage());
            }
            return body;
        }

        return ex.getMessage();
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
