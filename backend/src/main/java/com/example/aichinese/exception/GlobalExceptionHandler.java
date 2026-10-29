package com.example.aichinese.exception;

import static com.example.aichinese.dto.ApiResponse.failure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.aichinese.config.SecurityConfig;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final int MAX_FIELD_KEY_LENGTH = 80;

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
        String causeSummary = cause.getClass().getSimpleName();
        log.warn("Malformed or unreadable request body rejected with HTTP 400 [requestId={}]: {}",
                requestId(), causeSummary);
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported content type rejected with HTTP 415 [requestId={}]: {}",
                requestId(), exception.getContentType());
        return invalidRequest(exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        log.warn("Unsupported HTTP method rejected with HTTP 405 [requestId={}]: {}",
                requestId(), exception.getMethod());
        return invalidRequest(exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        if (exception.getStatusCode().is5xxServerError()) {
            // The user only sees a friendly message, so a server-side failure must at least reach the log.
            log.error("Request failed with HTTP {} [requestId={}]", exception.getStatusCode().value(), requestId(), exception);
        }
        String message = FriendlyErrorMessageResolver.resolve(exception.getStatusCode(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders()).body(failure(message));
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
                exception.getConstraintViolations().size());
        return validationBadRequest(extractFieldErrors(exception));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        log.warn("Argument type mismatch rejected request with HTTP 400 [requestId={}]: parameter '{}'",
                requestId(),
                exception.getName());
        return badRequest(FriendlyErrorMessageResolver.invalidRequestMessage());
    }

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
        return invalidRequest(exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(failure(FriendlyErrorMessageResolver.notFoundMessage()));
    }

    /** Also handles {@link MaxUploadSizeExceededException}, a MultipartException itself. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        if (exception.contains(MaxUploadSizeExceededException.class)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(failure(FriendlyErrorMessageResolver.uploadTooLargeMessage()));
        }
        return badRequest(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    /** A required multipart part (for example {@code file}) is missing: a client error, not a server fault. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestPartException(
            MissingServletRequestPartException exception) {
        log.warn("Missing multipart part rejected with HTTP 400 [requestId={}]: {}",
                requestId(), exception.getRequestPartName());
        return badRequest(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception) {
        log.warn("Database integrity constraint rejected request [requestId={}]: {}",
                requestId(), exception.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(failure(FriendlyErrorMessageResolver.conflictMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException exception) {
        log.warn("Concurrent update rejected request [requestId={}]: {}",
                requestId(), exception.getMostSpecificCause().getClass().getSimpleName());
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

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(failure(message));
    }

    /** The exception's own status and headers (such as {@code Allow} or {@code Accept}) with the generic message. */
    private static ResponseEntity<ApiResponse<Void>> invalidRequest(ErrorResponse exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(failure(FriendlyErrorMessageResolver.invalidRequestMessage()));
    }

    private static ResponseEntity<ApiResponse<Void>> validationBadRequest(Map<String, List<String>> fieldErrors) {
        Map<String, List<String>> safeFieldErrors = shouldExposeFieldErrors() ? fieldErrors : Map.of();
        return ResponseEntity.badRequest()
                .body(failure(FriendlyErrorMessageResolver.validationFailedMessage(), safeFieldErrors));
    }

    private static Map<String, List<String>> extractFieldErrors(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> addFieldError(fieldErrors, error.getField(), error.getDefaultMessage()));
        return fieldErrors;
    }

    private static Map<String, List<String>> extractFieldErrors(ConstraintViolationException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                addFieldError(fieldErrors, lastPathSegment(violation.getPropertyPath()), violation.getMessage()));
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

    private static String normalizeFieldKey(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }

        StringBuilder safe = new StringBuilder(field.length());
        boolean previousWasSeparator = false;
        for (int index = 0; index < field.length(); index++) {
            char ch = field.charAt(index);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                safe.append(ch);
                previousWasSeparator = false;
            } else if ((ch == '.' || ch == '[' || ch == ']') && !previousWasSeparator) {
                safe.append('.');
                previousWasSeparator = true;
            }
        }

        int start = 0;
        while (start < safe.length() && safe.charAt(start) == '.') {
            start++;
        }
        int end = safe.length();
        while (end > start && safe.charAt(end - 1) == '.') {
            end--;
        }
        return start == end ? null : safe.substring(start, end);
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
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        String path = attributes.getRequest().getRequestURI();
        return path == null || !SecurityConfig.PUBLIC_AUTH_POST_ENDPOINTS.contains(path);
    }

    private static String requestId() {
        return RequestIdHolder.currentOrDash();
    }
}
