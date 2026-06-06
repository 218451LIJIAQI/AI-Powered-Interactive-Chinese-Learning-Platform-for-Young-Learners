package com.example.aichinese.exception;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
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
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNKNOWN_CAUSE = "unknown";

    private static final int MAX_CAUSE_DEPTH = 20;

    private static final String REQUEST_FIELD = "request";

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
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

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException exception) {
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

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(failure(message));
    }

    private static ResponseEntity<ApiResponse<Void>> validationBadRequest(Map<String, List<String>> fieldErrors) {
        String message = FriendlyErrorMessageResolver.validationFailedMessage();
        Map<String, List<String>> safeFieldErrors = shouldExposeFieldErrors() ? fieldErrors : Map.of();
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(message, List.of(message), safeFieldErrors));
    }

    private static ApiResponse<Void> failure(String message) {
        return ApiResponse.failure(message, List.of(message));
    }

    private static Map<String, List<String>> extractFieldErrors(MethodArgumentNotValidException exception) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            addFieldError(fieldErrors, field, message);
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
            addFieldError(fieldErrors, field, message);
        });
        return copyFieldErrors(fieldErrors);
    }

    private static Map<String, List<String>> extractFieldErrors(HandlerMethodValidationException exception) {
        if (exception.getParameterValidationResults().isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError error : parameterErrors.getFieldErrors()) {
                    addFieldError(fieldErrors, error.getField(), error.getDefaultMessage());
                }
                parameterErrors.getGlobalErrors().forEach(error ->
                        addFieldError(fieldErrors, parameterName(result), error.getDefaultMessage()));
                continue;
            }

            String field = parameterName(result);
            result.getResolvableErrors().forEach(error ->
                    addFieldError(fieldErrors, fieldName(field, error), error.getDefaultMessage()));
        }
        return copyFieldErrors(fieldErrors);
    }

    private static void addFieldError(Map<String, List<String>> fieldErrors, String field, String message) {
        String safeField = normalizeFieldKey(field);
        if (safeField == null || message == null || message.isBlank()) {
            return;
        }
        fieldErrors.computeIfAbsent(safeField, ignored -> new ArrayList<>())
                .add(message.strip());
    }

    private static String parameterName(ParameterValidationResult result) {
        if (result == null || result.getMethodParameter() == null) {
            return REQUEST_FIELD;
        }
        result.getMethodParameter().initParameterNameDiscovery(PARAMETER_NAME_DISCOVERER);
        String parameterName = result.getMethodParameter().getParameterName();
        return parameterName == null || parameterName.isBlank() ? REQUEST_FIELD : parameterName;
    }

    private static String fieldName(String fallback, MessageSourceResolvable error) {
        String[] codes = error == null ? null : error.getCodes();
        if (codes != null) {
            for (String code : codes) {
                String safeCode = normalizeFieldKey(code);
                if (safeCode != null) {
                    return safeCode;
                }
            }
        }
        return fallback;
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
        return Collections.unmodifiableMap(copied);
    }

    private static boolean shouldExposeFieldErrors() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        String path = attributes.getRequest().getRequestURI();
        return path == null || !PUBLIC_AUTH_VALIDATION_PATHS.contains(path);
    }

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

    private static Set<Throwable> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>(8));
    }

    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
