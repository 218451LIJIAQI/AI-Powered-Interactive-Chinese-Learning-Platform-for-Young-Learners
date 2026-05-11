package com.example.aichinese.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.aichinese.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        ApiResponse<Void> response = ApiResponse.failure("Validation failed", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String causeSummary = cause == null ? exception.getMessage() : cause.getClass().getSimpleName();
        log.warn("Malformed or unreadable request body rejected with HTTP 400: {}", causeSummary);
        String message = "Request body is missing or malformed. Please check the JSON payload and try again.";
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? "Request could not be completed."
                : exception.getReason();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(exception.getStatusCode()).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = safeMessage(exception, "Request was invalid. Please check the input and try again.");
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations()
                .stream()
                .map(violation -> {
                    String path = violation.getPropertyPath() == null
                            ? ""
                            : violation.getPropertyPath().toString();
                    return path.isBlank()
                            ? violation.getMessage()
                            : path + ": " + violation.getMessage();
                })
                .toList();
        ApiResponse<Void> response = ApiResponse.failure("Validation failed", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        String parameterName = exception.getName() == null ? "parameter" : exception.getName();
        String message = "Invalid value for parameter '" + parameterName + "'.";
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        String resource = exception.getResourcePath() == null || exception.getResourcePath().isBlank()
                ? "Requested resource"
                : "Resource '" + exception.getResourcePath() + "'";
        String message = resource + " was not found.";
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(RuntimeException exception) {
        String message = safeMessage(exception, "You do not have permission to perform this action.");
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        String message = "Uploaded file exceeds the maximum allowed file size.";
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        String message = "Uploaded file could not be processed. Please check the file type and size.";
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception exception) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", exception);
        ApiResponse<Void> response = ApiResponse.failure(
                "An unexpected error occurred. Please try again later.",
                List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    /**
     * Returns the exception message when present and non-blank, otherwise the
     * supplied user-facing fallback. Guards every error response against a null
     * `message` field and a `["null"]` errors list when a thrown exception did
     * not carry a meaningful description.
     */
    private String safeMessage(Throwable exception, String fallback) {
        if (exception == null) {
            return fallback;
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
