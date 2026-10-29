package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.exception.GlobalExceptionHandler;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Unit tests for {@link GlobalExceptionHandler}: safe fallbacks for null/blank messages,
 * status mapping (400/403/405/409/413/415/429/500), request-id propagation and when
 * field errors are exposed or suppressed.
 */
class GlobalExceptionHandlerTests {

    private static final String REQUEST_ID = "audit-request-123";
    private static final String GENERIC_CLIENT_ERROR =
            "The request could not be processed. Please check the input and try again.";
    private static final String CONFLICT_MESSAGE =
            "The request conflicts with the current state. Please refresh and try again.";
    private static final String PAYLOAD_TOO_LARGE_MESSAGE =
            "The file is too large. Please choose a smaller file and try again.";
    private static final String VALIDATION_MESSAGE =
            "The information you entered is incomplete or invalid. Please check it and try again.";
    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "You are doing that too often. Please wait a moment and try again.";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        RequestIdHolder.unbind();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "   ")
    void illegalArgumentExceptionWithoutMessageReturnsSafeFallback(String message) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException(message));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = body(response);
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isNotBlank().doesNotContainIgnoringCase("null");
        assertThat(body.errors()).isNotEmpty().noneMatch(item -> item == null || item.isBlank());
    }

    @Test
    void illegalArgumentExceptionWithMessagePreservesOriginalMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Specific business rule violated."));

        ApiResponse<Void> body = body(response);
        assertThat(body.message()).isEqualTo("Specific business rule violated.");
        assertThat(body.errors()).containsExactly("Specific business rule violated.");
    }

    @Test
    void dailyAiLimitMessageReachesTheLearnerWhileOtherRateLimitsStayGeneric() {
        ResponseEntity<ApiResponse<Void>> daily = handler.handleResponseStatusException(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Daily AI request limit reached. Please try again tomorrow."));
        ResponseEntity<ApiResponse<Void>> minute = handler.handleResponseStatusException(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "AI request limit reached. Please wait a minute before trying again."));

        assertThat(daily.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body(daily).message()).isEqualTo("Daily AI request limit reached. Please try again tomorrow.");
        assertThat(body(minute).message()).isEqualTo(TOO_MANY_REQUESTS_MESSAGE);
    }

    @Test
    void liveClassAccessMessagesReachTheStudentWhileOtherForbiddenMessagesStayGeneric() {
        for (String message : List.of(
                "This live class is not available.",
                "The live class join window is not open yet.")) {
            ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
                    new ResponseStatusException(HttpStatus.FORBIDDEN, message));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(body(response).message()).isEqualTo(message);
        }
        ResponseEntity<ApiResponse<Void>> other = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal ownership check failed for lesson 42."));
        assertThat(body(other).message()).isEqualTo(FriendlyErrorMessageResolver.permissionDeniedMessage());
    }

    @Test
    void errorResponseIncludesCurrentRequestIdWhenAvailable() {
        RequestIdHolder.bind(REQUEST_ID);

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Specific business rule violated."));

        assertThat(body(response).requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void maxUploadSizeExceededReturnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMultipartException(
                new MaxUploadSizeExceededException(1024L));

        assertPayloadTooLarge(response);
    }

    @Test
    void multipartWrappedUploadTooLargeReturnsPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMultipartException(
                new MultipartException("multipart failed", new MaxUploadSizeExceededException(1024L)));

        assertPayloadTooLarge(response);
    }

    /** SDD T27: an unexpected server failure returns a generic message without internals. */
    @Test
    void unexpectedExceptionReturnsGenericServerErrorWithoutInternals() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
                new IllegalStateException("select * from users where password='secret' token=abc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Void> body = body(response);
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo(FriendlyErrorMessageResolver.serverUnavailableMessage());
        assertThat(body.errors()).containsExactly(body.message());
        assertThat(body.message()).doesNotContain("select", "secret", "token");
    }

    @Test
    void missingMultipartFilePartReturnsBadRequestInsteadOfServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    @Test
    void unsupportedMediaTypeReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_FORM_URLENCODED,
                        List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
        assertGenericClientError(response);
    }

    @Test
    void unsupportedMethodReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(
                HttpMethod.GET,
                HttpMethod.POST);
        assertGenericClientError(response);
    }

    @Test
    void databaseIntegrityViolationReturnsConflictWithoutLeakingSqlDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("Duplicate entry 'student@example.com' for key 'users.email'"));

        assertConflict(response);
        assertThat(body(response).message()).doesNotContain("student@example.com", "users.email", "Duplicate entry");
    }

    @Test
    void optimisticLockingFailureReturnsConflict() {
        assertConflict(handler.handleOptimisticLockingFailureException(
                new ObjectOptimisticLockingFailureException("LessonProgress", 10L)));
    }

    @Test
    void authenticatedValidationReturnsSafeFieldErrors() throws Exception {
        bindRequestPath("/api/v1/teachers/profile");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(
                validationException(
                        fieldError("displayName", "Display name is required."),
                        fieldError("email", "Email is required.")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = body(response);
        assertThat(body.message()).isEqualTo(VALIDATION_MESSAGE);
        assertThat(body.fieldErrors()).containsEntry("displayName", List.of("Display name is required."));
        assertThat(body.fieldErrors().keySet()).containsExactly("displayName", "email");
    }

    @Test
    void validationDropsOverlongFieldKeys() throws Exception {
        bindRequestPath("/api/v1/teachers/profile");

        ApiResponse<Void> body = body(handler.handleMethodArgumentNotValidException(validationException(
                fieldError("a".repeat(81), "Too long."),
                fieldError("b".repeat(80), "Kept."))));

        assertThat(body.fieldErrors().keySet()).containsExactly("b".repeat(80));
    }

    @Test
    void publicAuthValidationSuppressesFieldErrors() throws Exception {
        bindRequestPath("/api/v1/auth/login");

        ApiResponse<Void> body = body(handler.handleMethodArgumentNotValidException(
                validationException(fieldError("email", "Email address is required."))));

        assertThat(body.fieldErrors()).isEmpty();
        assertThat(body.errors()).containsExactly(body.message());
    }

    private static void bindRequestPath(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static ApiResponse<Void> body(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getBody()).as("response body").isNotNull();
        return response.getBody();
    }

    private static void assertPayloadTooLarge(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(body(response).message()).isEqualTo(PAYLOAD_TOO_LARGE_MESSAGE);
    }

    private static void assertConflict(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).success()).isFalse();
        assertThat(body(response).message()).isEqualTo(CONFLICT_MESSAGE);
    }

    private static void assertGenericClientError(ResponseEntity<ApiResponse<Void>> response) {
        ApiResponse<Void> body = body(response);
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo(GENERIC_CLIENT_ERROR);
        assertThat(body.errors()).containsExactly(body.message());
    }

    private static MethodArgumentNotValidException validationException(FieldError... errors) throws Exception {
        SampleRequest target = new SampleRequest("");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sampleRequest");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTests.class.getDeclaredMethod("sampleEndpoint", SampleRequest.class),
                0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    private static FieldError fieldError(String field, String message) {
        return new FieldError("sampleRequest", field, "", false, null, null, message);
    }

    @SuppressWarnings("unused")
    private static void sampleEndpoint(@Valid SampleRequest request) {
    }

    private record SampleRequest(@NotBlank String displayName) {
    }
}
