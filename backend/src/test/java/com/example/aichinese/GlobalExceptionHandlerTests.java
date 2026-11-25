package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.exception.GlobalExceptionHandler;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.validator.internal.engine.path.PathImpl;

/**
 * Unit tests for {@link GlobalExceptionHandler}: safe fallbacks for null/blank messages,
 * status mapping (400/403/405/409/413/415/429/500), request-id propagation and when
 * field errors are exposed or suppressed.
 */
class GlobalExceptionHandlerTests {

    private static final String REQUEST_ID = "audit-request-123";
    private static final String PROFILE_PATH = "/api/v1/users/me/profile";
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
    }

    @Test
    void illegalArgumentExceptionReturnsGenericClientErrorWithoutLeakingInternalDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("No enum constant com.example.aichinese.entity.enums.RoleName.FOO"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertGenericClientError(response);
        assertThat(body(response).message()).doesNotContain("com.example", "RoleName");
    }

    @Test
    void serverErrorStatusNeverPassesThroughExceptionReasonEvenWhenHintSubstringMatches() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal error: account is currently unavailable while loading shard 2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body(response).message()).isEqualTo(FriendlyErrorMessageResolver.serverUnavailableMessage());
    }

    @Test
    void blankStatusExceptionMessagesUseStatusSpecificFriendlyFallbacks() {
        assertThat(FriendlyErrorMessageResolver.resolve(HttpStatus.UNAUTHORIZED, null))
                .isEqualTo(FriendlyErrorMessageResolver.sessionExpiredMessage());
        assertThat(FriendlyErrorMessageResolver.resolve(HttpStatus.FORBIDDEN, " "))
                .isEqualTo(FriendlyErrorMessageResolver.permissionDeniedMessage());
        assertThat(FriendlyErrorMessageResolver.resolve(HttpStatus.NOT_FOUND, null))
                .isEqualTo(FriendlyErrorMessageResolver.notFoundMessage());
        assertThat(FriendlyErrorMessageResolver.resolve(HttpStatus.TOO_MANY_REQUESTS, null))
                .isEqualTo(TOO_MANY_REQUESTS_MESSAGE);
    }

    @ParameterizedTest
    @CsvSource({
            "true, Daily AI request limit reached. Please try again tomorrow.",
            "true, We just sent a link. Please wait a minute before asking for another one.",
            "true, You asked for several links today. Please try again tomorrow.",
            "false, AI request limit reached. Please wait a minute before trying again.",
            "false, Too many requests.",
            "false, Too many failed attempts."
    })
    void onlySpecificRateLimitMessagesReachTheUser(boolean showMessage, String message) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body(response).message()).isEqualTo(showMessage ? message : TOO_MANY_REQUESTS_MESSAGE);
    }

    @Test
    void accountMessagesReachTheUserWhileOtherForbiddenMessagesStayGeneric() {
        String message = "This account is currently unavailable. Please contact the platform administrator.";
        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, message));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(response).message()).isEqualTo(message);
        assertThat(body(handler.handleResponseStatusException(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Internal ownership check failed for lesson 42.")))
                .message()).isEqualTo(FriendlyErrorMessageResolver.permissionDeniedMessage());
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
        assertThat(body.message()).doesNotContain("select", "secret", "token");
    }

    @Test
    void missingMultipartFilePartReturnsBadRequestInsteadOfServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestPartException(
                new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).message()).isEqualTo(FriendlyErrorMessageResolver.uploadFailedMessage());
    }

    @Test
    void unsupportedMediaTypeReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedRequest(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_FORM_URLENCODED,
                        List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
        assertGenericClientError(response);
    }

    @Test
    void unsupportedMethodReturnsClientErrorEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnsupportedRequest(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(
                HttpMethod.GET,
                HttpMethod.POST);
        assertGenericClientError(response);
    }

    @Test
    void databaseIntegrityViolationReturnsConflictWithoutLeakingSqlDetails() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataConflict(
                new DataIntegrityViolationException("Duplicate entry 'student@example.com' for key 'users.email'"));

        assertConflict(response);
        assertThat(body(response).message()).doesNotContain("student@example.com", "users.email", "Duplicate entry");
    }

    @Test
    void staleVersionsDeadlocksAndLockTimeoutsAreConflicts() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        List<DataAccessException> conflicts = List.of(
                new ObjectOptimisticLockingFailureException("LessonProgress", 10L),
                new CannotAcquireLockException("Deadlock found when trying to get lock"),
                new PessimisticLockingFailureException("Lock wait timeout exceeded"));

        for (DataAccessException conflict : conflicts) {
            assertThat(resolver.resolveMethodByThrowable(conflict).getName()).isEqualTo("handleDataConflict");
            assertConflict(handler.handleDataConflict(conflict));
        }
    }

    @Test
    void authenticatedValidationReturnsSafeFieldErrors() throws Exception {
        bindRequestPath(PROFILE_PATH);

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
        bindRequestPath(PROFILE_PATH);

        ApiResponse<Void> body = body(handler.handleMethodArgumentNotValidException(validationException(
                fieldError("a".repeat(81), "Too long."),
                fieldError("b".repeat(80), "Kept."))));

        assertThat(body.fieldErrors().keySet()).containsExactly("b".repeat(80));
    }

    @Test
    void validationFlattensNestedFieldKeysAndStripsUnsafeCharacters() throws Exception {
        bindRequestPath(PROFILE_PATH);

        ApiResponse<Void> body = body(handler.handleMethodArgumentNotValidException(validationException(
                fieldError("items[0].name", "Nested."),
                fieldError("bad<key>", "Unsafe."),
                fieldError("[]", "Nothing left."))));

        assertThat(body.fieldErrors().keySet()).containsExactly("items.0.name", "badkey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/school"})
    void publicAuthValidationSuppressesFieldErrors(String contextPath) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + "/api/v1/auth/login");
        request.setContextPath(contextPath);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ApiResponse<Void> body = body(handler.handleMethodArgumentNotValidException(
                validationException(fieldError("email", "Email address is required."))));

        assertThat(body.fieldErrors()).isEmpty();
        assertThat(body.message()).isEqualTo(VALIDATION_MESSAGE);
    }

    @Test
    void handlerMethodValidationSuppressesBindingFailureFieldErrors() throws Exception {
        bindRequestPath(PROFILE_PATH);
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "sampleRequest");
        errors.addError(new FieldError("sampleRequest", "topic", "BAD", true, null, null,
                "Failed to convert property value of type 'java.lang.String' to 'com.example.aichinese.Topic'"));
        errors.addError(fieldError("message", "Message is required."));
        ParameterErrors paramErrors = new ParameterErrors(parameter, new Object(), errors, null, null, null);
        MethodValidationResult result = MethodValidationResult.create(
                new Object(), parameter.getMethod(), List.<ParameterValidationResult>of(paramErrors));

        ApiResponse<Void> body = body(handler.handleMethodValidationException(new HandlerMethodValidationException(result)));

        assertThat(body.fieldErrors()).containsOnlyKeys("message");
        assertThat(body.fieldErrors().get("message")).containsExactly("Message is required.");
    }

    @Test
    void constraintViolationExtractsLeafParameterOnAuthenticatedRoutesAndSuppressesPublicAuth() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(PathImpl.createPathFromString("list.pageSize"));
        when(violation.getMessage()).thenReturn("Must be at least 1.");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        bindRequestPath(PROFILE_PATH);
        ApiResponse<Void> authenticated = body(handler.handleConstraintViolationException(exception));
        assertThat(authenticated.fieldErrors()).containsEntry("pageSize", List.of("Must be at least 1."));

        bindRequestPath("/api/v1/auth/login");
        ApiResponse<Void> publicAuth = body(handler.handleConstraintViolationException(exception));
        assertThat(publicAuth.fieldErrors()).isEmpty();
    }

    @Test
    void clientDisconnectReturnsNullWithoutErrorEnvelope() {
        assertThat(handler.handleClientDisconnect(new AsyncRequestNotUsableException("Broken pipe"))).isNull();
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
    }

    private static MethodArgumentNotValidException validationException(FieldError... errors) throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "sampleRequest");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        // The handler reads only the binding result; the exception merely requires some method parameter.
        return new MethodArgumentNotValidException(
                new MethodParameter(Object.class.getMethod("equals", Object.class), 0), bindingResult);
    }

    private static FieldError fieldError(String field, String message) {
        return new FieldError("sampleRequest", field, "", false, null, null, message);
    }
}
