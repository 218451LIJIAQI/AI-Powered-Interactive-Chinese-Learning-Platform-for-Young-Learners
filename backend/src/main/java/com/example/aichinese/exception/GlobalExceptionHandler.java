package com.example.aichinese.exception;

import static com.example.aichinese.dto.ApiResponse.failure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.UrlPathHelper;

import com.example.aichinese.config.SecurityConfig;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final int MAX_FIELD_KEY_LENGTH = 80;
    private static final Pattern UNSAFE_FIELD_KEY_CHARACTERS = Pattern.compile("[^\\p{L}\\p{Nd}_.\\[\\]-]");
    private static final Pattern FIELD_KEY_SEPARATORS = Pattern.compile("[.\\[\\]]+");
    private static final Pattern OUTER_DOTS = Pattern.compile("^\\.|\\.$");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        log.warn("Bean validation rejected request body with HTTP 400 [requestId={}]: {} field error(s)",
                RequestIdHolder.currentOrDash(),
                exception.getBindingResult().getErrorCount());
        return validationBadRequest(extractFieldErrors(exception));
    }

    /** A body that cannot be read, a path or query value of the wrong type, or a missing parameter, header or cookie. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(Exception exception) {
        log.warn("Unreadable request rejected with HTTP 400 [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName());
        ExceptionLogUtils.debugOrigin(log, exception);
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    /**
     * An unsupported content type, HTTP method or response type: the exception's own status (415, 405 or 406) and
     * headers (such as {@code Accept} or {@code Allow}) with the generic message.
     */
    @ExceptionHandler({
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedRequest(ErrorResponse exception) {
        String summary = exception instanceof Throwable throwable
                ? ExceptionLogUtils.safeSummary(throwable)
                : exception.getClass().getSimpleName();
        log.warn("Unsupported request rejected with HTTP {} [requestId={}]: {}",
                exception.getStatusCode().value(), RequestIdHolder.currentOrDash(), summary);
        return jsonError(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(failure(FriendlyErrorMessageResolver.invalidRequestMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        if (exception.getStatusCode().is5xxServerError()) {
            // The user only sees a friendly message, so a server-side failure reaches the log in redacted form.
            log.error("Request failed with HTTP {} [requestId={}]: {}",
                    exception.getStatusCode().value(),
                    RequestIdHolder.currentOrDash(),
                    ExceptionLogUtils.safeSummary(exception));
            ExceptionLogUtils.debugOrigin(log, exception);
        }
        String message = FriendlyErrorMessageResolver.resolve(exception.getStatusCode(), exception.getReason());
        return jsonError(exception.getStatusCode()).headers(exception.getHeaders()).body(failure(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.warn("Illegal argument rejected request with HTTP 400 [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), ExceptionLogUtils.safeSummary(exception));
        ExceptionLogUtils.debugOrigin(log, exception);
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        log.warn("Constraint violation rejected request with HTTP 400 [requestId={}]: {} violation(s)",
                RequestIdHolder.currentOrDash(),
                exception.getConstraintViolations().size());
        return validationBadRequest(extractFieldErrors(exception));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidationException(
            HandlerMethodValidationException exception) {
        log.warn("Handler method validation rejected request with HTTP 400 [requestId={}]: {} error(s)",
                RequestIdHolder.currentOrDash(),
                exception.getAllErrors().size());
        return validationBadRequest(extractFieldErrors(exception));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException() {
        return jsonError(HttpStatus.NOT_FOUND)
                .body(failure(FriendlyErrorMessageResolver.notFoundMessage()));
    }

    /** Also handles {@link MaxUploadSizeExceededException}, a MultipartException itself. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        if (exception.contains(MaxUploadSizeExceededException.class)) {
            return jsonError(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(failure(FriendlyErrorMessageResolver.uploadTooLargeMessage()));
        }
        log.warn("Multipart request rejected with HTTP 400 [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName());
        ExceptionLogUtils.debugOrigin(log, exception);
        return badRequest(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    /** A required multipart part (for example {@code file}) is missing: a client error, not a server fault. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException exception) {
        log.warn("Missing multipart part rejected with HTTP 400 [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), exception.getRequestPartName());
        return badRequest(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    /**
     * A database constraint or a concurrent transaction refused the write: a stale version, a deadlock or a lock wait
     * that timed out. Trying again is the answer, so it is a conflict, not a server fault.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleDataConflict(DataAccessException exception) {
        log.warn("Database conflict rejected request [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), exception.getMostSpecificCause().getClass().getSimpleName());
        ExceptionLogUtils.debugOrigin(log, exception);
        return jsonError(HttpStatus.CONFLICT)
                .body(failure(FriendlyErrorMessageResolver.conflictMessage()));
    }

    /** Client closed the connection mid-stream (video seeking, aborted SSE): nothing can be written back. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleClientDisconnect(AsyncRequestNotUsableException exception) {
        log.debug("Client disconnected before response finished [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), ExceptionLogUtils.safeSummary(exception));
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        String summary = cause == exception
                ? ExceptionLogUtils.safeSummary(exception)
                : ExceptionLogUtils.safeSummary(exception) + " (cause=" + ExceptionLogUtils.safeSummary(cause) + ")";
        log.error("Unhandled exception caught by GlobalExceptionHandler [requestId={}]: {}",
                RequestIdHolder.currentOrDash(), summary);
        ExceptionLogUtils.debugOrigin(log, exception);
        return jsonError(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(failure(FriendlyErrorMessageResolver.serverUnavailableMessage()));
    }

    /** Successful requests may produce a stream or a file. The API error envelope always uses JSON. */
    private static ResponseEntity.BodyBuilder jsonError(HttpStatusCode status) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return jsonError(HttpStatus.BAD_REQUEST).body(failure(message));
    }

    private static ResponseEntity<ApiResponse<Void>> validationBadRequest(Map<String, List<String>> fieldErrors) {
        Map<String, List<String>> safeFieldErrors = shouldExposeFieldErrors() ? fieldErrors : Map.of();
        return jsonError(HttpStatus.BAD_REQUEST)
                .body(failure(FriendlyErrorMessageResolver.validationFailedMessage(), safeFieldErrors));
    }

    /**
     * Only the constraint messages, which are written for users. A form field that could not be converted (an unknown
     * support topic) has a default message full of Java type names; the response then only has the generic message.
     */
    private static Map<String, List<String>> extractFieldErrors(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .filter(error -> !error.isBindingFailure())
                .forEach(error -> addFieldError(fieldErrors, error.getField(), error.getDefaultMessage()));
        return fieldErrors;
    }

    private static Map<String, List<String>> extractFieldErrors(ConstraintViolationException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                addFieldError(fieldErrors, lastPathSegment(violation.getPropertyPath()), violation.getMessage()));
        return fieldErrors;
    }

    private static Map<String, List<String>> extractFieldErrors(HandlerMethodValidationException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
                    return;
                }
                String field = error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : paramName;
                addFieldError(fieldErrors, field, error.getDefaultMessage());
            });
        });
        return fieldErrors;
    }

    private static void addFieldError(Map<String, List<String>> fieldErrors, String field, String message) {
        String safeField = normalizeFieldKey(field);
        if (safeField == null || safeField.length() > MAX_FIELD_KEY_LENGTH || message == null || message.isBlank()) {
            return;
        }
        fieldErrors.computeIfAbsent(safeField, ignored -> new ArrayList<>())
                .add(message.strip());
    }

    /**
     * Letters, digits, {@code _} and {@code -}, with each run of {@code . [ ]} as one dot ({@code items[0].name}
     * becomes {@code items.0.name}) and none at the ends; {@code null} when nothing remains.
     */
    private static String normalizeFieldKey(String field) {
        if (field == null) {
            return null;
        }
        String safe = UNSAFE_FIELD_KEY_CHARACTERS.matcher(field).replaceAll("");
        safe = FIELD_KEY_SEPARATORS.matcher(safe).replaceAll(".");
        safe = OUTER_DOTS.matcher(safe).replaceAll("");
        return safe.isEmpty() ? null : safe;
    }

    private static String lastPathSegment(Path path) {
        String last = null;
        for (Path.Node node : path) {
            if (node.getName() != null && !node.getName().isBlank()) {
                last = node.getName();
            }
        }
        return last;
    }

    private static boolean shouldExposeFieldErrors() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && !SecurityConfig.PUBLIC_POST_ENDPOINTS.contains(
                        UrlPathHelper.defaultInstance.getPathWithinApplication(attributes.getRequest()));
    }
}
