package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.service.NoOpEmailService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * End-to-end MockMvc coverage of {@code /api/v1/auth/**}: registration
 * (student + teacher), login, current-user lookup, logout,
 * forgot-password, reset-password, plus rate-limiter + cache-header
 * + user-enumeration behaviours.
 *
 * <p>Pins the security-critical invariants:</p>
 * <ul>
 *   <li>Login lockout after threshold via
 *       {@code LoginAttemptLimiter}.</li>
 *   <li>Forgot-password 429 via {@code PublicAuthRequestLimiter} (the register
 *       quota is unit-tested in PublicAuthRequestLimiterTests).</li>
 *   <li>Forgot-password response is identical whether or not the
 *       email exists.</li>
 *   <li>Reset tokens are single-use, 60-minute TTL, only the hash is
 *       stored.</li>
 *   <li>{@code /me} carries {@code Cache-Control: no-store}.</li>
 *   <li>Teacher accounts start PENDING and can sign in.</li>
 *   <li>Logout bumps the {@code sessionVersion} so existing JWTs are
 *       revoked instantly.</li>
 * </ul>
 */
@TestPropertySource(properties = "app.security.forwarded.trusted-proxies[0]=198.51.100.0/24")
class AuthControllerIntegrationTests extends ApiIntegrationTest {

    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("reset form:\\s*([A-Za-z0-9]+)");
    private static final String REFRESH_COOKIE_NAME = "mandarin_spark_refresh";
    private static final String REFRESH_COOKIE_PREFIX = REFRESH_COOKIE_NAME + "=";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String REGISTER_STUDENT_PATH = "/api/v1/auth/register/student";
    private static final String REGISTER_TEACHER_PATH = "/api/v1/auth/register/teacher";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    private static final String RESET_PASSWORD_PATH = "/api/v1/auth/reset-password";
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String WRONG_PASSWORD = "WrongPassword!";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoSpyBean
    private NoOpEmailService noOpEmailService;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {
        String email = uniqueEmail("student");

        MvcResult result = mockMvc.perform(api.jsonPost(REGISTER_STUDENT_PATH, null, Map.of(
                        "fullName", "Student One",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "ms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        User user = user(email);
        assertThat(user.getPreferredLanguage()).isEqualTo("ms");
        assertThat(user.getRoles()).extracting(role -> role.getName().name()).containsExactly("STUDENT");
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(studentProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRegisterTeacherSuccessfullyWithPendingStatus() throws Exception {
        String email = uniqueEmail("teacher");

        mockMvc.perform(teacherRegistration(email, "Friendly Mandarin teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email));

        assertThat(user(email).getRoles()).extracting(role -> role.getName().name()).containsExactly("TEACHER");
        assertThat(teacherProfile(email).getTeacherStatus()).isEqualTo(TeacherStatus.PENDING);
    }

    @Test
    void shouldRejectTeacherRegistrationWithoutAValidBio() throws Exception {
        // Same rule as the sign-up form: the teaching bio is required and must be 20 to 1000 characters.
        for (String bio : Arrays.asList(null, "  I teach Mandarin.  ", "b".repeat(1001))) {
            String email = uniqueEmail("teacher-invalid-bio");
            mockMvc.perform(teacherRegistration(email, bio))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
            assertThat(userRepository.findByEmailWithRoles(email)).isEmpty();
        }
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = uniqueEmail("duplicate");
        registerStudentAccount(email);

        mockMvc.perform(studentRegistration(email, USER_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }

    @Test
    void shouldLoginSuccessfullyForStudent() throws Exception {
        String email = uniqueEmail("login-student");
        registerStudentAccount(email);
        String requestId = "req-login-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(api.loginRequest(email, USER_PASSWORD).header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(REFRESH_COOKIE_PREFIX)))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("HttpOnly")))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("SameSite=Strict")))
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash", "password_hash");
        assertRefreshCookieIssuedAndLegacyCookieCleared(result);
    }

    @Test
    void shouldRefreshAccessTokenRotateRefreshCookieAndRejectReplay() throws Exception {
        String email = uniqueEmail("refresh-rotate");
        registerStudentAccount(email);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String initialAccessToken = accessToken(loginResult);
        String initialRefreshToken = refreshCookieValue(loginResult);

        MvcResult refreshResult = mockMvc.perform(refresh(initialRefreshToken))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(REFRESH_COOKIE_PREFIX)))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String rotatedAccessToken = accessToken(refreshResult);
        String rotatedRefreshToken = refreshCookieValue(refreshResult);
        assertThat(rotatedAccessToken).isNotEqualTo(initialAccessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);
        assertRefreshCookieIssuedAndLegacyCookieCleared(refreshResult);

        mockMvc.perform(api.authGet(ME_PATH, rotatedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // A second tab sending the same cookie right after the rotation is refused, and the first tab keeps its
        // new session.
        mockMvc.perform(refresh(initialRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(api.authGet(ME_PATH, rotatedAccessToken))
                .andExpect(status().isOk());

        // Later, the same replay is treated as a stolen token: the whole sign-in is revoked.
        RefreshToken consumed = refreshTokensOf(user(email))
                .filter(token -> token.getRotatedAt() != null)
                .findFirst()
                .orElseThrow();
        consumed.setRotatedAt(LocalDateTime.now().minus(RefreshTokenService.ROTATION_GRACE_WINDOW).minusSeconds(1));
        refreshTokenRepository.save(consumed);

        mockMvc.perform(refresh(initialRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(api.authGet(ME_PATH, rotatedAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(refresh(rotatedRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCrossSiteBrowserRefreshWithoutConsumingRefreshToken() throws Exception {
        String refreshToken = signedInRefreshToken("refresh-origin");

        mockMvc.perform(refresh(refreshToken)
                        .header("Origin", "https://evil.example")
                        .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldRejectSameSiteBrowserRefreshWithoutTrustedOriginMetadata() throws Exception {
        String refreshToken = signedInRefreshToken("refresh-same-site");

        mockMvc.perform(refresh(refreshToken).header("Sec-Fetch-Site", "same-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowSameOriginBrowserRefreshWithoutOriginOrReferer() throws Exception {
        mockMvc.perform(refresh(signedInRefreshToken("refresh-same-origin-fetch")).header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowRefreshFromConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(refresh(signedInRefreshToken("refresh-allowed-origin")).header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldClearRefreshCookieAndRevokeRefreshTokensOnLogout() throws Exception {
        String email = uniqueEmail("refresh-logout");
        registerStudentAccount(email);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String refreshToken = refreshCookieValue(loginResult);
        User user = user(email);
        RefreshToken issuedRefreshToken = activeRefreshTokens(user).findFirst().orElseThrow();
        assertThat(issuedRefreshToken.getUpdatedAt()).isNotNull();

        MvcResult logoutResult = mockMvc.perform(api.authPost(LOGOUT_PATH, accessToken(loginResult))
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(REFRESH_COOKIE_PREFIX + ";")))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("Max-Age=0")))
                .andReturn();

        assertRefreshCookieCleared(logoutResult, REFRESH_PATH);
        assertRefreshCookieCleared(logoutResult, LEGACY_REFRESH_COOKIE_PATH);

        RefreshToken revokedRefreshToken = refreshTokenRepository.findById(Objects.requireNonNull(issuedRefreshToken.getId()))
                .orElseThrow();
        assertThat(revokedRefreshToken.getRevokedAt()).isNotNull();
        assertThat(revokedRefreshToken.getUpdatedAt()).isEqualTo(revokedRefreshToken.getRevokedAt());
        assertThat(activeRefreshTokens(user)).isEmpty();

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void forgotPasswordAuditLogShouldCaptureRequestTraceId() throws Exception {
        String email = uniqueEmail("forgot-trace");
        registerStudentAccount(email);
        String requestId = "req-forgot-" + UUID.randomUUID();
        long maxAuditIdBefore = maxAuditId();

        mockMvc.perform(forgotPassword(email).header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true));

        AuditLog auditLog = auditLogsAfter(maxAuditIdBefore, "PASSWORD_RESET_REQUESTED").findFirst().orElseThrow();
        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        String email = uniqueEmail("wrong-password");
        registerStudentAccount(email);

        mockMvc.perform(api.loginRequest(email, "WrongPassword999!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        // An unregistered email gets the same general message, so accounts cannot be enumerated (T02).
        mockMvc.perform(api.loginRequest(uniqueEmail("never-registered"), "WrongPassword999!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void authEndpointsShouldRejectOversizedBoundaryInputs() throws Exception {
        mockMvc.perform(api.loginRequest("student@example.com", "P".repeat(73)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Well-formed addresses at and one character over the 150-character limit, so only the size check differs.
        // A separate client address keeps these requests out of the shared forgot-password rate-limit bucket.
        String domain = "d".repeat(41) + "." + "e".repeat(40) + ".com";
        String emailAtLimit = UUID.randomUUID().toString().replace("-", "") + "x".repeat(31) + "@" + domain;
        assertThat(emailAtLimit).hasSize(150);
        mockMvc.perform(forgotPassword(emailAtLimit).with(fromRemoteAddress("203.0.113.150")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(forgotPassword("y" + emailAtLimit).with(fromRemoteAddress("203.0.113.150")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Matching passwords, so the 72-character limit (not the confirm-password check) is what rejects the request.
        String passwordAtLimit = USER_PASSWORD + "x".repeat(72 - USER_PASSWORD.length());
        mockMvc.perform(studentRegistration(uniqueEmail("boundary-student"), passwordAtLimit + "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(studentRegistration(uniqueEmail("boundary-student"), passwordAtLimit))
                .andExpect(status().isOk());

        // An unknown token is a 400 as well, so the message shows the 128-character limit rejected it first.
        mockMvc.perform(resetPassword("t".repeat(129), USER_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "The information you entered is incomplete or invalid. Please check it and try again."));
    }

    @Test
    void shouldAllowAdminLoginFromSeededAccount() throws Exception {
        mockMvc.perform(api.loginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedStudent() throws Exception {
        String email = uniqueEmail("me-student");
        String token = api.registerStudent(email);

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.studentProfile").isMap())
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                // Prevent profile replay from browser cache after logout.
                .andExpect(noStore());
    }

    @Test
    void shouldRejectExistingTokenAfterUserIsDeactivated() throws Exception {
        String email = uniqueEmail("inactive-token");
        String token = api.registerStudent(email);

        User user = user(email);
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectExistingTokenAfterExplicitLogout() throws Exception {
        String email = uniqueEmail("logout-token");
        String token = api.registerStudent(email);

        mockMvc.perform(api.authPost(LOGOUT_PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(activeRefreshTokens(user(email))).isEmpty();

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedTeacher() throws Exception {
        String email = uniqueEmail("me-teacher");
        String token = api.registerTeacher(email);

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherProfile").isMap())
                .andExpect(jsonPath("$.data.teacherProfile.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.studentProfile").doesNotExist());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedAdminWithoutProfiles() throws Exception {
        MvcResult result = mockMvc.perform(api.authGet(ME_PATH, api.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"studentProfile\":null", "\"teacherProfile\":null")
                .doesNotContain("passwordHash", "password_hash");
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/students/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesAdminRoute() throws Exception {
        String token = api.registerStudent(uniqueEmail("forbidden-student"));

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action. Contact an administrator if needed."));
    }

    // Teacher -> admin dashboard and student -> teacher dashboard (403) are covered in DashboardControllerIntegrationTests.

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("could not be processed")
                .doesNotContain("Exception");
    }

    @Test
    void shouldReturnBadRequestWhenPathVariableTypeDoesNotMatch() throws Exception {
        String token = api.registerStudent(uniqueEmail("path-type"));

        MvcResult result = mockMvc.perform(api.authGet("/api/v1/students/classes/lessons/not-a-number", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The request could not be processed. Please check the input and try again."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception", "at org.springframework");
    }

    @Test
    void shouldReturnNotFoundForUnknownAuthenticatedApiRoute() throws Exception {
        // Under the default-deny SecurityConfig an unmapped path is only reachable when the request is
        // authenticated, so a signed-in student hits a non-existent student path and the GlobalExceptionHandler
        // turns the NoResourceFoundException into a clean 404.
        String token = api.registerStudent(uniqueEmail("not-found"));

        MvcResult result = mockMvc.perform(api.authGet(
                        "/api/v1/students/this-route-does-not-exist-" + UUID.randomUUID(), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("We could not find the item you requested. Please refresh and try again."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception", "at org.springframework");
    }

    @Test
    void shouldRejectResetPasswordWithUnknownToken() throws Exception {
        // Unknown and already-used reset tokens intentionally share the same public error.
        mockMvc.perform(resetPassword("not-a-real-token-" + UUID.randomUUID(), "NewPassword123!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("invalid")));
    }

    @Test
    void shouldRejectResetPasswordWithBlankToken() throws Exception {
        mockMvc.perform(resetPassword("   ", "NewPassword123!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldCompletePasswordResetEndToEndAndAllowLoginWithNewPasswordExactlyOnce() throws Exception {
        String email = uniqueEmail("reset-flow");
        registerStudentAccount(email);
        String originalToken = api.login(email, USER_PASSWORD);
        String originalRefreshToken = refreshCookieValue(login(email, USER_PASSWORD));

        String rawResetToken = issueResetToken(email);

        User user = user(email);
        PasswordResetToken token = activeResetTokens(user).findFirst()
                .orElseThrow(() -> new AssertionError("Reset token was not persisted for the registered email."));
        // 60-minute lifetime (SDD 5.1).
        assertThat(token.getExpiresAt()).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusMinutes(61));
        assertThat(token.getTokenHash()).isNotBlank().isNotEqualTo(rawResetToken);

        mockMvc.perform(resetPassword(rawResetToken, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(activeRefreshTokens(user)).isEmpty();

        mockMvc.perform(refresh(originalRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // The old password must no longer work; the new password must.
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(api.loginRequest(email, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        mockMvc.perform(api.authGet(ME_PATH, originalToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Single-use enforcement: the same token cannot be redeemed a second time.
        mockMvc.perform(resetPassword(rawResetToken, "AnotherNew789!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectExpiredPasswordResetTokenAndKeepTheOldPassword() throws Exception {
        // FRAUTH09: a reset link past its expiry time cannot change the password.
        String email = uniqueEmail("reset-expired");
        registerStudentAccount(email);
        String rawResetToken = issueResetToken(email);

        PasswordResetToken token = activeResetTokens(user(email)).findFirst()
                .orElseThrow(() -> new AssertionError("Reset token was not persisted for the registered email."));
        // expires_at is not updatable through JPA, so age the token directly in the table.
        int aged = jdbcTemplate.update(
                "update password_reset_tokens set expires_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1),
                token.getId());
        assertThat(aged).isEqualTo(1);

        mockMvc.perform(resetPassword(rawResetToken, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(passwordResetTokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNull();
        assertThat(api.login(email, USER_PASSWORD)).isNotBlank();
        mockMvc.perform(api.loginRequest(email, NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIssueFreshTokenAndInvalidatePreviousActiveTokenWhenForgotPasswordIsCalledTwice() throws Exception {
        // A newer reset request invalidates older still-active links.
        String email = uniqueEmail("reset-rotate");
        registerStudentAccount(email);

        String firstRawToken = issueResetToken(email);

        User user = user(email);
        PasswordResetToken firstToken = resetTokensOf(user).findFirst().orElseThrow();
        assertThat(firstToken.getTokenHash()).isNotBlank().isNotEqualTo(firstRawToken);

        String secondRawToken = issueResetToken(email);
        assertThat(secondRawToken).isNotEqualTo(firstRawToken);

        // The original token is marked used and only the newer token remains active.
        assertThat(activeResetTokens(user)).hasSize(1);

        mockMvc.perform(resetPassword(firstRawToken, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldConsumePasswordResetTokenOnlyOnceAtRepositoryBoundary() throws Exception {
        String email = uniqueEmail("reset-atomic");
        registerStudentAccount(email);
        issueResetToken(email);

        Long tokenId = Objects.requireNonNull(activeResetTokens(user(email)).findFirst()
                .orElseThrow(() -> new AssertionError("Reset token was not persisted for the registered email."))
                .getId());
        LocalDateTime consumeAt = LocalDateTime.now();

        int firstConsume = passwordResetTokenRepository.consumeActiveToken(tokenId, consumeAt);
        int replayConsume = passwordResetTokenRepository.consumeActiveToken(tokenId, LocalDateTime.now());

        assertThat(firstConsume).isEqualTo(1);
        assertThat(replayConsume).isZero();
        PasswordResetToken consumedToken = passwordResetTokenRepository.findById(tokenId).orElseThrow();
        assertThat(consumedToken.getUsedAt()).isEqualTo(consumedToken.getUpdatedAt());
        assertThat(Math.abs(ChronoUnit.MICROS.between(consumeAt, consumedToken.getUsedAt())))
                .isLessThanOrEqualTo(1L);
    }

    @Test
    void shouldReturnConsistentForgotPasswordResponseRegardlessOfEmailExistence() throws Exception {
        // The endpoint returns the same public message for a registered and an unknown address, so it cannot be
        // used to probe which addresses are registered on the platform.
        String registeredEmail = uniqueEmail("forgot-existing");
        registerStudentAccount(registeredEmail);

        MvcResult existingResult = mockMvc.perform(forgotPassword(registeredEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        MvcResult missingResult = mockMvc.perform(forgotPassword(uniqueEmail("definitely-not-registered")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode existingBody = objectMapper.readTree(existingResult.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(missingResult.getResponse().getContentAsString());
        assertThat(existingBody.path("message").asText()).isEqualTo(missingBody.path("message").asText());
        assertThat(existingBody.path("data").isNull()).isTrue();
        assertThat(missingBody.path("data").isNull()).isTrue();
    }

    @Test
    void shouldRateLimitForgotPasswordRequests() throws Exception {
        String email = uniqueEmail("forgot-limit");
        String forwardedIp = "198.51.100.200";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(forgotPassword(email).header("X-Forwarded-For", forwardedIp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(forgotPassword(email).header("X-Forwarded-For", forwardedIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromUntrustedRemoteAddress() throws Exception {
        // A new e-mail for every request, so only the per-IP bucket can trigger the 429 below: if the spoofed
        // header were trusted, the first ten requests would count against the spoofed IP instead.
        String actualRemoteIp = "203.0.113.200";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(forgotPassword(uniqueEmail("forgot-untrusted"))
                            .with(fromRemoteAddress(actualRemoteIp))
                            .header("X-Forwarded-For", "203.0.113.55"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(forgotPassword(uniqueEmail("forgot-untrusted")).with(fromRemoteAddress(actualRemoteIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnUnauthorizedForUnmappedApiRouteWithoutToken() throws Exception {
        // Default deny: a path SecurityConfig does not list is a 401 without authentication, never let through.
        mockMvc.perform(get("/api/v1/some-future-route-without-explicit-rule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldLockOutLoginAfterRepeatedFailedAttempts() throws Exception {
        // Brute-force defence: after the LoginAttemptLimiter threshold (5) further attempts are refused with 429
        // before the password is checked. A fresh unique account keeps the limiter bucket independent of the
        // other tests in this JVM.
        String email = uniqueEmail("lockout-target");
        registerStudentAccount(email);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));

        // Even the correct password is refused while the bucket is locked.
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginLockoutAuditRecordShouldNotStoreEmailOrIpAddress() throws Exception {
        String email = uniqueEmail("lockout-audit");
        registerStudentAccount(email);
        long maxAuditIdBefore = maxAuditId();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD).header("X-Forwarded-For", "198.51.100.44"))
                    .andExpect(status().isUnauthorized());
        }

        AuditLog lockoutAudit = auditLogsAfter(maxAuditIdBefore, "LOGIN_LOCKOUT")
                .filter(auditLog -> "USER".equals(auditLog.getTargetType()))
                .filter(auditLog -> auditLog.getDescription() != null
                        && auditLog.getDescription().contains("repeated failed attempts"))
                .findFirst()
                .orElseThrow();

        assertThat(lockoutAudit.getDescription()).doesNotContain(email);
    }

    /** Registers a student without signing in, so the test controls every sign-in session. */
    private void registerStudentAccount(String email) throws Exception {
        mockMvc.perform(studentRegistration(email, USER_PASSWORD))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder studentRegistration(String email, String password) throws Exception {
        return api.jsonPost(REGISTER_STUDENT_PATH, null, Map.of(
                "fullName", "Student Test",
                "email", email,
                "password", password,
                "confirmPassword", password,
                "preferredLanguage", "en"));
    }

    /** A teacher sign-up; a {@code null} bio is left out of the request. */
    private MockHttpServletRequestBuilder teacherRegistration(String email, String bio) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of(
                "fullName", "Teacher One",
                "email", email,
                "password", USER_PASSWORD,
                "confirmPassword", USER_PASSWORD));
        if (bio != null) {
            request.put("bio", bio);
        }
        return api.jsonPost(REGISTER_TEACHER_PATH, null, request);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(api.loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Registers a student, signs in and returns the refresh-cookie value of that sign-in. */
    private String signedInRefreshToken(String emailPrefix) throws Exception {
        String email = uniqueEmail(emailPrefix);
        registerStudentAccount(email);
        return refreshCookieValue(login(email, USER_PASSWORD));
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) {
        return post(REFRESH_PATH).cookie(refreshCookie(refreshToken));
    }

    private MockHttpServletRequestBuilder forgotPassword(String email) throws Exception {
        return api.jsonPost(FORGOT_PASSWORD_PATH, null, Map.of("email", email));
    }

    private MockHttpServletRequestBuilder resetPassword(String token, String password) throws Exception {
        return api.jsonPost(RESET_PASSWORD_PATH, null, Map.of(
                "token", token,
                "password", password,
                "confirmPassword", password));
    }

    private Stream<RefreshToken> refreshTokensOf(User user) {
        return refreshTokenRepository.findAll().stream().filter(token -> token.getUser().getId().equals(user.getId()));
    }

    private Stream<RefreshToken> activeRefreshTokens(User user) {
        return refreshTokensOf(user).filter(token -> token.getRevokedAt() == null);
    }

    private Stream<PasswordResetToken> resetTokensOf(User user) {
        return passwordResetTokenRepository.findAll().stream().filter(token -> token.getUser().getId().equals(user.getId()));
    }

    private Stream<PasswordResetToken> activeResetTokens(User user) {
        return resetTokensOf(user).filter(token -> token.getUsedAt() == null);
    }

    private long maxAuditId() {
        return auditLogRepository.findAll().stream().mapToLong(AuditLog::getId).max().orElse(0L);
    }

    /** The audit rows of {@code actionType} written after the row {@code maxAuditIdBefore}. */
    private Stream<AuditLog> auditLogsAfter(long maxAuditIdBefore, String actionType) {
        return auditLogRepository.findAll().stream()
                .filter(auditLog -> auditLog.getId() > maxAuditIdBefore)
                .filter(auditLog -> actionType.equals(auditLog.getActionType()));
    }

    private String accessToken(MvcResult result) throws Exception {
        return api.data(result).path("token").asText();
    }

    private String refreshCookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(SET_COOKIE_HEADER);
        assertThat(setCookie).isNotBlank().contains(REFRESH_COOKIE_PREFIX);
        String cookieValue = setCookie.substring(REFRESH_COOKIE_PREFIX.length());
        int separatorIndex = cookieValue.indexOf(';');
        return separatorIndex >= 0 ? cookieValue.substring(0, separatorIndex) : cookieValue;
    }

    private void assertRefreshCookieIssuedAndLegacyCookieCleared(MvcResult result) {
        assertThat(refreshCookieWithPath(result, REFRESH_PATH))
                .contains(REFRESH_COOKIE_PREFIX, "HttpOnly", "SameSite=Strict")
                .doesNotContain("Max-Age=0");
        assertRefreshCookieCleared(result, LEGACY_REFRESH_COOKIE_PATH);
    }

    private void assertRefreshCookieCleared(MvcResult result, String path) {
        assertThat(refreshCookieWithPath(result, path)).contains(REFRESH_COOKIE_PREFIX + ";", "Max-Age=0");
    }

    private String refreshCookieWithPath(MvcResult result, String path) {
        return result.getResponse().getHeaders(SET_COOKIE_HEADER).stream()
                .filter(cookie -> cookie.contains(REFRESH_COOKIE_PREFIX))
                .filter(cookie -> cookie.contains("; Path=" + path + ";"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing refresh cookie for path " + path));
    }

    private String issueResetToken(String email) throws Exception {
        clearInvocations(noOpEmailService);

        mockMvc.perform(forgotPassword(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // E-mails are sent asynchronously after commit, so a reset e-mail of an earlier test may still arrive
        // here: only an e-mail addressed to this user counts (none arriving fails the verify).
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(noOpEmailService, timeout(2_000).atLeastOnce()).sendEmail(
                argThat(recipient -> recipient != null && email.equals(recipient.getEmail())),
                any(),
                bodyCaptor.capture());

        Matcher matcher = RESET_TOKEN_PATTERN.matcher(bodyCaptor.getAllValues().getLast());
        assertThat(matcher.find()).as("reset token should appear in the fallback email body").isTrue();
        return matcher.group(1);
    }

    private static MockCookie refreshCookie(String token) {
        return new MockCookie(REFRESH_COOKIE_NAME, token);
    }
}
