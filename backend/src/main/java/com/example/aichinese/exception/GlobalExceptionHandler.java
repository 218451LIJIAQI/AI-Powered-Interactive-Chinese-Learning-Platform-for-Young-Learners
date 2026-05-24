package com.example.aichinese.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolationException;

/**
 * Centralised, never-leaking translation layer from Spring / JPA /
 * security / multipart exceptions into the platform's uniform
 * {@link ApiResponse} envelope.
 *
 * <h2>Why a single class</h2>
 * <p>Every controller in the platform returns {@link ApiResponse}; every
 * thrown exception must therefore land here so the front-end (and any
 * future API client) sees the same {@code { success, message, errors }}
 * shape regardless of what failed deep in the stack. The order of the
 * handlers below is deliberately specific-to-generic, so a typed handler
 * (e.g. {@link MaxUploadSizeExceededException}) always wins over the
 * catch-all {@link Exception} handler at the bottom.</p>
 *
 * <h2>Privacy & log hygiene</h2>
 * <p>Every log line records only the exception class name (via
 * {@link #rootCauseClassName(Throwable)}) and the current request id
 * (via {@link RequestIdHolder}); raw exception messages are never
 * logged here because they often contain the user-supplied input that
 * triggered the failure (an email, a Stripe id, a path token). The
 * client-visible message is filtered through
 * {@link FriendlyErrorMessageResolver}, which intentionally collapses
 * many error variants into a small set of safe canonical messages so
 * we never expose stack-trace-style internals to the browser.</p>
 *
 * <h2>Validation handlers</h2>
 * <p>{@link MethodArgumentNotValidException} and
 * {@link ConstraintViolationException} are deliberately collapsed to the
 * single {@code "validation failed"} canonical message instead of being
 * expanded into per-field errors: the platform's DTO-level
 * {@code @NotBlank} / {@code @Size} / {@code @Pattern} messages are
 * already user-facing English strings, and surfacing them per-field can
 * leak internal field names. The front-end runs its own client-side
 * validation that produces matching field-level highlights, so server
 * messages remain coarse-grained.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        return badRequest(FriendlyErrorMessageResolver.validationFailedMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String causeSummary = cause == null
                ? exception.getClass().getSimpleName()
                : cause.getClass().getSimpleName();
        log.warn("Malformed or unreadable request body rejected with HTTP 400 [requestId={}]: {}",
                requestId(), causeSummary);
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported content type rejected with HTTP 415 [requestId={}]: {}",
                requestId(), exception.getContentType());
        ApiResponse<Void> response = failure(FriendlyErrorMessageResolver.invalidRequestMessage());
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        log.warn("Unsupported HTTP method rejected with HTTP 405 [requestId={}]: {}",
                requestId(), exception.getMethod());
        ApiResponse<Void> response = failure(FriendlyErrorMessageResolver.invalidRequestMessage());
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        String message = FriendlyErrorMessageResolver.resolve(exception.getStatusCode(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body(failure(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = FriendlyErrorMessageResolver.resolve(HttpStatus.BAD_REQUEST, exception.getMessage());
        return ResponseEntity.badRequest().body(failure(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        return badRequest(FriendlyErrorMessageResolver.validationFailedMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        return badRequest(FriendlyErrorMessageResolver.validationFailedMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(failure(FriendlyErrorMessageResolver.notFoundMessage()));
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(failure(FriendlyErrorMessageResolver.permissionDeniedMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(failure(FriendlyErrorMessageResolver.uploadTooLargeMessage()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        if (isUploadTooLarge(exception)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(failure(FriendlyErrorMessageResolver.uploadTooLargeMessage()));
        }
        return badRequest(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception) {
        log.warn("Database integrity constraint rejected request [requestId={}]: {}",
                requestId(), rootCauseClassName(exception));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(failure(FriendlyErrorMessageResolver.conflictMessage()));
    }

    @ExceptionHandler({ OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(RuntimeException exception) {
        log.warn("Concurrent update rejected request [requestId={}]: {}",
                requestId(), rootCauseClassName(exception));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(failure(FriendlyErrorMessageResolver.conflictMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception exception) {
        log.error("Unhandled exception caught by GlobalExceptionHandler [requestId={}]",
                requestId(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(failure(FriendlyErrorMessageResolver.serverUnavailableMessage()));
    }

    /**
     * DRY helper for the common "400 Bad Request with this canonical
     * message" response shape used by half the handlers in this class.
     */
    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(failure(message));
    }

    /**
     * Builds the canonical failure envelope used by every handler:
     * a non-success {@link ApiResponse} with the same message both at
     * the top level and inside the single-element {@code errors} list,
     * so the front-end can render either field at its preferred location
     * without conditional null checks.
     */
    private static ApiResponse<Void> failure(String message) {
        return ApiResponse.failure(message, List.of(message));
    }

    /**
     * Walks the {@link Throwable#getCause()} chain to look for an
     * {@link MaxUploadSizeExceededException} buried inside a generic
     * {@link MultipartException} wrapper — Tomcat / Jetty sometimes wrap
     * the size violation in a {@code MultipartException} rather than
     * surfacing the typed exception directly.
     */
    private boolean isUploadTooLarge(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Returns the simple class name of the innermost (root) cause in the
     * exception chain. Used in logs in place of the raw exception message,
     * which can echo user input back into the log file and risk PII leaks.
     */
    private String rootCauseClassName(Throwable exception) {
        if (exception == null) {
            return "unknown";
        }
        Throwable current = exception;
        Throwable root = exception;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root.getClass().getSimpleName();
    }

    /**
     * Resolves the current request's correlation id (set by
     * {@code RequestIdFilter}) for inclusion in log lines, falling back
     * to a literal {@code "-"} when no id is available (e.g. exceptions
     * thrown outside a normal HTTP request lifecycle such as scheduled
     * jobs).
     */
    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
