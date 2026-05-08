package com.example.aichinese.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;

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
                .collect(Collectors.toList());
        ApiResponse<Void> response = ApiResponse.failure("Validation failed", errors);
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
        ApiResponse<Void> response = ApiResponse.failure(exception.getMessage(), List.of(exception.getMessage()));
        return ResponseEntity.badRequest().body(response);
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
}
