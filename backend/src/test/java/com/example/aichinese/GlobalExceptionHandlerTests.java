package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.GlobalExceptionHandler;
import com.example.aichinese.util.RequestIdHolder;

/**
 * Unit tests for {@link GlobalExceptionHandler} that exercise the null/blank
 * exception-message safety net. These guarantees are not covered by the
 * integration tests because no service throws an unmessaged exception today,
 * but a future caller (or library) easily could.
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentExceptionWithNullMessageReturnsSafeFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException((String) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isNotBlank();
        assertThat(body.message()).doesNotContainIgnoringCase("null");
        assertThat(body.errors()).isNotEmpty();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void illegalArgumentExceptionWithBlankMessageReturnsSafeFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("   "));

        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isNotBlank();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void illegalArgumentExceptionWithMessagePreservesOriginalMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Specific business rule violated."));

        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isEqualTo("Specific business rule violated.");
        assertThat(body.errors()).containsExactly("Specific business rule violated.");
    }

    @Test
    void accessDeniedExceptionWithNullMessageReturnsForbiddenFallback() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDeniedException(
                new AccessDeniedException(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isNotBlank();
        assertThat(body.errors()).noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void errorResponseIncludesCurrentRequestIdWhenAvailable() {
        RequestIdHolder.set("audit-request-123");
        try {
            ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                    new IllegalArgumentException("Specific business rule violated."));

            ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
            assertThat(body.requestId()).isEqualTo("audit-request-123");
        } finally {
            RequestIdHolder.clear();
        }
    }

    @Test
    void maxUploadSizeExceededReturnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(1024L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isEqualTo("The file is too large. Please choose a smaller file and try again.");
    }

    @Test
    void multipartWrappedUploadTooLargeReturnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMultipartException(
                new MultipartException("multipart failed", new MaxUploadSizeExceededException(1024L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.message()).isEqualTo("The file is too large. Please choose a smaller file and try again.");
    }

    @Test
    void unsupportedMediaTypeReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_FORM_URLENCODED,
                        List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("The request could not be processed. Please check the input and try again.");
        assertThat(body.errors()).containsExactly(body.message());
    }

    @Test
    void unsupportedMethodReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(
                HttpMethod.GET,
                HttpMethod.POST);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("The request could not be processed. Please check the input and try again.");
        assertThat(body.errors()).containsExactly(body.message());
    }

    @Test
    void databaseIntegrityViolationReturnsConflictWithoutLeakingSqlDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("Duplicate entry 'student@example.com' for key 'users.email'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("The request conflicts with the current state. Please refresh and try again.");
        assertThat(body.message()).doesNotContain("student@example.com", "users.email", "Duplicate entry");
    }

    @Test
    void optimisticLockingFailureReturnsConflict() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLockingFailureException(
                new ObjectOptimisticLockingFailureException("PaymentRecord", 10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("The request conflicts with the current state. Please refresh and try again.");
    }

    @Test
    void authenticatedValidationReturnsSafeFieldErrors() throws Exception {
        bindRequestPath("/api/v1/teachers/profile");
        try {
            ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(
                    validationException("displayName", "Display name is required."));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
            assertThat(body.message()).isEqualTo(
                    "The information you entered is incomplete or invalid. Please check it and try again.");
            assertThat(body.fieldErrors()).containsEntry("displayName", List.of("Display name is required."));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void publicAuthValidationSuppressesFieldErrors() throws Exception {
        bindRequestPath("/api/v1/auth/login");
        try {
            ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(
                    validationException("email", "Email address is required."));

            ApiResponse<Void> body = Objects.requireNonNull(response.getBody(), "response body");
            assertThat(body.fieldErrors()).isEmpty();
            assertThat(body.errors()).containsExactly(body.message());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static void bindRequestPath(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static MethodArgumentNotValidException validationException(String field, String message) throws Exception {
        SampleRequest target = new SampleRequest("");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sampleRequest");
        bindingResult.addError(new FieldError("sampleRequest", field, "", false, null, null, message));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTests.class.getDeclaredMethod("sampleEndpoint", SampleRequest.class),
                0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private static void sampleEndpoint(@Valid SampleRequest request) {
    }

    private record SampleRequest(@NotBlank String displayName) {
    }
}
