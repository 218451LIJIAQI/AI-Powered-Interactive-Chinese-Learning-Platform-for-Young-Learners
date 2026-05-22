package com.example.aichinese.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.aichinese.dto.ApiResponse;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        String message = FriendlyErrorMessageResolver.validationFailedMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String causeSummary = cause == null ? exception.getMessage() : cause.getClass().getSimpleName();
        log.warn("Malformed or unreadable request body rejected with HTTP 400: {}", causeSummary);
        String message = FriendlyErrorMessageResolver.invalidRequestMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported content type rejected with HTTP 415: {}", exception.getContentType());
        String message = FriendlyErrorMessageResolver.invalidRequestMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
        log.warn("Unsupported HTTP method rejected with HTTP 405: {}", exception.getMethod());
        String message = FriendlyErrorMessageResolver.invalidRequestMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception) {
        String message = FriendlyErrorMessageResolver.resolve(exception.getStatusCode(), exception.getReason());
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(exception.getStatusCode()).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = FriendlyErrorMessageResolver.resolve(HttpStatus.BAD_REQUEST, exception.getMessage());
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String message = FriendlyErrorMessageResolver.validationFailedMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception) {
        String message = FriendlyErrorMessageResolver.validationFailedMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        String message = FriendlyErrorMessageResolver.invalidRequestMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception) {
        String message = FriendlyErrorMessageResolver.notFoundMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(RuntimeException exception) {
        String message = FriendlyErrorMessageResolver.permissionDeniedMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        String message = FriendlyErrorMessageResolver.uploadTooLargeMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException exception) {
        if (isUploadTooLarge(exception)) {
            String message = FriendlyErrorMessageResolver.uploadTooLargeMessage();
            ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
        }

        String message = FriendlyErrorMessageResolver.uploadFailedMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception exception) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", exception);
        String message = FriendlyErrorMessageResolver.serverUnavailableMessage();
        ApiResponse<Void> response = ApiResponse.failure(message, List.of(message));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

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
}
