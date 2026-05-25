package com.example.aichinese.exception;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

/**
 * Centralised, never-leaking translation layer from Spring / JPA /
 * security / multipart exceptions into the platform's uniform
 * {@link ApiResponse} envelope.
 *
 * <h2>Why a single class</h2>
 * <p>Every controller in the platform returns {@link ApiResponse}; every
 * thrown exception must therefore land here so the front-end (and any
 * future API client) sees the same
 * {@code { success, message, data, errors, fieldErrors, requestId }} shape
 * regardless of what failed deep in the stack. The order of the
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
 * <p>Validation responses preserve the coarse top-level
 * {@code "validation failed"} message for every client. For authenticated
 * form routes, the envelope also includes a safe {@code fieldErrors} map so
 * the UI can anchor messages next to fields. Public authentication routes
 * keep that map empty to avoid account-enumeration and credential-policy
 * hints.</p>
 *
 * <h2>Advice ordering</h2>
 * <p>The class is annotated {@link Order @Order(HIGHEST_PRECEDENCE)} so
 * that any future {@link org.springframework.web.bind.annotation.ControllerAdvice}
 * added to the platform cannot accidentally pre-empt the centralised
 * envelope translation — every controller-thrown exception must flow
 * through here so the wire format never diverges.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Sentinel returned when the cause chain cannot be inspected. */
    private static final String UNKNOWN_CAUSE = "unknown";

    /**
     * Hard cap on cause-chain traversal depth. Realistic JVM exceptions
     * never nest beyond a handful of levels; this cap exists purely to
     * defend against pathological or self-referential cause chains that
     * would otherwise turn an exception handler into an infinite loop.
     */
    private static final int MAX_CAUSE_DEPTH = 20;

    private static final Set<String> PUBLIC_AUTH_VALIDATION_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register/student",
            "/api/v1/auth/register/teacher",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        log.warn("Bean validation rejected request body with HTTP 400 [requestId={}]: {} field error(s)",
                requestId(),
                exception.getBindingResult().getErrorCount());
        return validationBadRequest(extractFieldErrors(exception));
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
        log.warn("Constraint violation rejected request with HTTP 400 [requestId={}]: {} violation(s)",
                requestId(),
                exception.getConstraintViolations() == null ? 0 : exception.getConstraintViolations().size());
        return validationBadRequest(extractFieldErrors(exception));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        log.warn("Handler-method validation rejected request with HTTP 400 [requestId={}]: {} error(s)",
                requestId(),
                exception.getAllErrors().size());
        return validationBadRequest(Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        log.warn("Argument type mismatch rejected request with HTTP 400 [requestId={}]: parameter '{}'",
                requestId(),
                exception.getName());
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    /**
     * Catches missing-required-parameter / missing-path-variable /
     * missing-required-header / missing-required-cookie failures from
     * Spring's request binder. Without this handler those exceptions
     * would fall through to the catch-all {@code Exception} branch and
     * be reported as HTTP 500, which would mis-attribute a client error
     * as a server fault.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleServletRequestBindingException(
            ServletRequestBindingException exception) {
        log.warn("Request binding failed with HTTP 400 [requestId={}]: {}",
                requestId(),
                exception.getClass().getSimpleName());
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotAcceptableException(
            HttpMediaTypeNotAcceptableException exception) {
        log.warn("Unacceptable media type rejected with HTTP 406 [requestId={}]", requestId());
        ApiResponse<Void> response = failure(FriendlyErrorMessageResolver.invalidRequestMessage());
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(response);
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

    private static ResponseEntity<ApiResponse<Void>> validationBadRequest(Map<String, List<String>> fieldErrors) {
        String message = FriendlyErrorMessageResolver.validationFailedMessage();
        Map<String, List<String>> safeFieldErrors = shouldExposeFieldErrors() ? fieldErrors : Map.of();
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(message, List.of(message), safeFieldErrors));
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

    private static Map<String, List<String>> extractFieldErrors(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            if (field != null && message != null && !message.isBlank()) {
                fieldErrors.computeIfAbsent(field, ignored -> new java.util.ArrayList<>())
                        .add(message.strip());
            }
        });
        return copyFieldErrors(fieldErrors);
    }

    private static Map<String, List<String>> extractFieldErrors(ConstraintViolationException exception) {
        if (exception.getConstraintViolations() == null || exception.getConstraintViolations().isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String field = lastPathSegment(violation.getPropertyPath());
            String message = violation.getMessage();
            if (field != null && message != null && !message.isBlank()) {
                fieldErrors.computeIfAbsent(field, ignored -> new java.util.ArrayList<>())
                        .add(message.strip());
            }
        });
        return copyFieldErrors(fieldErrors);
    }

    private static String lastPathSegment(Path path) {
        if (path == null) {
            return null;
        }
        String last = null;
        for (Path.Node node : path) {
            if (node != null && node.getName() != null && !node.getName().isBlank()) {
                last = node.getName();
            }
        }
        return last;
    }

    private static Map<String, List<String>> copyFieldErrors(Map<String, List<String>> source) {
        if (source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        source.forEach((field, messages) -> copied.put(field, List.copyOf(messages)));
        return Map.copyOf(copied);
    }

    private static boolean shouldExposeFieldErrors() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        String path = attributes.getRequest().getRequestURI();
        return path == null || !PUBLIC_AUTH_VALIDATION_PATHS.contains(path);
    }

    /**
     * Walks the {@link Throwable#getCause()} chain to look for an
     * {@link MaxUploadSizeExceededException} buried inside a generic
     * {@link MultipartException} wrapper — Tomcat / Jetty sometimes wrap
     * the size violation in a {@code MultipartException} rather than
     * surfacing the typed exception directly.
     *
     * <p>The traversal is bounded by both an {@link IdentityHashMap}
     * cycle guard and {@link #MAX_CAUSE_DEPTH} so a pathological or
     * self-referential cause chain cannot turn this helper into an
     * infinite loop.</p>
     */
    private static boolean isUploadTooLarge(Throwable exception) {
        Set<Throwable> visited = newIdentitySet();
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH && visited.add(current)) {
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
     *
     * <p>The traversal is bounded by both an {@link IdentityHashMap}
     * cycle guard and {@link #MAX_CAUSE_DEPTH} so a pathological or
     * self-referential cause chain cannot turn this helper into an
     * infinite loop.</p>
     */
    private static String rootCauseClassName(Throwable exception) {
        if (exception == null) {
            return UNKNOWN_CAUSE;
        }
        Set<Throwable> visited = newIdentitySet();
        Throwable current = exception;
        Throwable root = exception;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH && visited.add(current)) {
            root = current;
            current = current.getCause();
        }
        return root.getClass().getSimpleName();
    }

    /**
     * Allocates a small identity-equality {@link Set} suitable for
     * tracking already-visited exceptions during cause-chain traversal.
     * Identity comparison (via {@link IdentityHashMap}) is required
     * because two distinct exception instances may legitimately share
     * {@link Object#equals(Object) equals} / {@link Object#hashCode()}
     * implementations and we only want to detect literal cycles.
     */
    private static Set<Throwable> newIdentitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>(8));
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
