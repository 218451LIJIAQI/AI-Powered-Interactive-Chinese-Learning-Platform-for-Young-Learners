package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.nextClientIp;
import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.TransactionalEmailDispatcher.EmailDeliveryRequested;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.Cookie;

/**
 * End-to-end MockMvc coverage of {@code /api/v1/auth/**}: registration
 * (learners only), login, current-user lookup, logout,
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
 *   <li>There is no teacher sign-up.</li>
 *   <li>Logout ends the browser's session (its refresh tokens and the JWTs
 *       that name it) at once; other devices stay signed in.</li>
 * </ul>
 */
@RecordApplicationEvents
class AuthControllerIntegrationTests extends ApiIntegrationTest {

    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("[?&]token=([A-Za-z0-9]+)");
    private static final String REFRESH_COOKIE_NAME = "mandarin_spark_refresh";
    private static final String REFRESH_COOKIE_PREFIX = REFRESH_COOKIE_NAME + "=";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String REGISTER_TEACHER_PATH = "/api/v1/auth/register/teacher";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    private static final String RESET_PASSWORD_PATH = "/api/v1/auth/reset-password";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String WRONG_PASSWORD = "WrongPassword!";

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents events;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {
        String email = uniqueEmail("student");

        MvcResult result = mockMvc.perform(api.jsonPost("/api/v1/auth/register/student", null, Map.of(
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

    /** Only learners sign up: the teacher sign-up is gone and creates no account. */
    @Test
    void theTeacherSignUpIsGone() throws Exception {
        String email = uniqueEmail("teacher");

        mockMvc.perform(teacherRegistration(email))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(teacherRegistration(email).header("Authorization", "Bearer " + api.adminToken()))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findByEmailWithRoles(email)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = uniqueEmail("duplicate");
        api.registerStudentAccount(email);

        mockMvc.perform(api.studentRegistrationRequest(email, USER_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }

    @Test
    void shouldLoginSuccessfullyForStudent() throws Exception {
        String email = uniqueEmail("login-student");
        api.registerStudentAccount(email);
        String requestId = "req-login-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(api.loginRequest(email, USER_PASSWORD).header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash", "password_hash");
        assertRefreshCookieIssued(result);
    }

    @Test
    void sessionCookieFollowsTheApplicationContextThroughLoginRefreshAndLogout() throws Exception {
        String context = "/school";
        String cookiePath = "Path=" + context + REFRESH_PATH;
        MvcResult login = mockMvc.perform(api.jsonPost(context + "/api/v1/auth/login", null,
                        Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)).contextPath(context))
                .andExpect(status().isOk())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(cookiePath)))
                .andReturn();
        MvcResult refresh = mockMvc.perform(post(context + REFRESH_PATH).contextPath(context)
                        .cookie(refreshCookie(refreshCookieValue(login))))
                .andExpect(status().isOk())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(cookiePath)))
                .andReturn();
        mockMvc.perform(post(context + LOGOUT_PATH).contextPath(context)
                        .header("Authorization", "Bearer " + accessToken(refresh)))
                .andExpect(status().isOk())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(cookiePath)))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("Max-Age=0")));
    }

    @Test
    void shouldRefreshAccessTokenRotateRefreshCookieAndRejectReplay() throws Exception {
        String email = uniqueEmail("refresh-rotate");
        api.registerStudentAccount(email);

        MvcResult loginResult = login(email);
        String initialAccessToken = accessToken(loginResult);
        String initialRefreshToken = refreshCookieValue(loginResult);

        MvcResult refreshResult = mockMvc.perform(refresh(initialRefreshToken))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String rotatedAccessToken = accessToken(refreshResult);
        String rotatedRefreshToken = refreshCookieValue(refreshResult);
        assertThat(rotatedAccessToken).isNotEqualTo(initialAccessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);
        assertRefreshCookieIssued(refreshResult);

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
        // The whole row is aged, as production's CHECKs require: issued before it was rotated (and revoked with it).
        // created_at is not updatable through JPA, so the row is changed in the table (bound as Timestamps).
        LocalDateTime rotatedAt = LocalDateTime.now().minus(RefreshTokenService.ROTATION_GRACE_WINDOW).minusSeconds(1);
        int aged = jdbc.update(
                "update refresh_tokens set created_at = ?, rotated_at = ?, revoked_at = ? where user_id = ? and rotated_at is not null",
                Timestamp.valueOf(rotatedAt.minusMinutes(1)),
                Timestamp.valueOf(rotatedAt),
                Timestamp.valueOf(rotatedAt),
                user(email).getId());
        assertThat(aged).isEqualTo(1);

        mockMvc.perform(refresh(initialRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(api.authGet(ME_PATH, rotatedAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(refresh(rotatedRefreshToken))
                .andExpect(status().isUnauthorized());
    }

    /** A rejected refresh does not use up the token: the same cookie still works without the untrusted header. */
    @ParameterizedTest
    @CsvSource({
            "Origin, https://evil.example",
            "Origin, not a url",
            "Referer, https://evil.example/page",
            "Sec-Fetch-Site, cross-site",
            "Sec-Fetch-Site, same-site"
    })
    void shouldRejectUntrustedBrowserRefreshWithoutConsumingRefreshToken(String headerName, String headerValue)
            throws Exception {
        String refreshToken = signedInRefreshToken("refresh-untrusted");

        mockMvc.perform(refresh(refreshToken).header(headerName, headerValue))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @ParameterizedTest
    @CsvSource({"Sec-Fetch-Site, same-origin", "Origin, http://localhost:5174", "Referer, http://localhost:5174/sign-in"})
    void shouldAllowBrowserRefreshFromSameOriginOrConfiguredFrontend(String headerName, String headerValue)
            throws Exception {
        mockMvc.perform(refresh(signedInRefreshToken("refresh-allowed")).header(headerName, headerValue))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldClearRefreshCookieAndRevokeRefreshTokensOnLogout() throws Exception {
        String email = uniqueEmail("refresh-logout");
        api.registerStudentAccount(email);

        MvcResult loginResult = login(email);
        String refreshToken = refreshCookieValue(loginResult);
        User user = user(email);
        RefreshToken issuedRefreshToken = refreshTokenRepository.findById(activeRefreshTokenIds(user).getFirst()).orElseThrow();
        assertThat(issuedRefreshToken.getUpdatedAt()).isNotNull();

        MvcResult logoutResult = mockMvc.perform(api.authPost(LOGOUT_PATH, accessToken(loginResult))
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertRefreshCookieCleared(logoutResult);

        RefreshToken revokedRefreshToken = refreshTokenRepository.findById(issuedRefreshToken.getId()).orElseThrow();
        assertThat(revokedRefreshToken.getRevokedAt()).isNotNull();
        assertThat(revokedRefreshToken.getUpdatedAt()).isEqualTo(revokedRefreshToken.getRevokedAt());
        assertThat(activeRefreshTokenIds(user)).isEmpty();

        mockMvc.perform(refresh(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void forgotPasswordAuditLogShouldCaptureRequestTraceId() throws Exception {
        String email = uniqueEmail("forgot-trace");
        api.registerStudentAccount(email);
        String requestId = "req-forgot-" + UUID.randomUUID();

        mockMvc.perform(forgotPassword(email).with(fromRemoteAddress(nextClientIp())).header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true));

        assertThat(auditForRequest(requestId, "PASSWORD_RESET_REQUESTED")).isPresent();
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        String email = uniqueEmail("wrong-password");
        api.registerStudentAccount(email);

        mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        // An unregistered email gets the same general message, so accounts cannot be enumerated (T02).
        mockMvc.perform(api.loginRequest(uniqueEmail("never-registered"), WRONG_PASSWORD))
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
        String limitIp = nextClientIp();
        mockMvc.perform(forgotPassword(emailAtLimit).with(fromRemoteAddress(limitIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(forgotPassword("y" + emailAtLimit).with(fromRemoteAddress(limitIp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Matching passwords, so the 72-character limit (not the confirm-password check) is what rejects the request.
        String passwordAtLimit = USER_PASSWORD + "x".repeat(72 - USER_PASSWORD.length());
        mockMvc.perform(api.studentRegistrationRequest(uniqueEmail("boundary-student"), passwordAtLimit + "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(api.studentRegistrationRequest(uniqueEmail("boundary-student"), passwordAtLimit))
                .andExpect(status().isOk());

        // An unknown token is a 400 as well, so the message shows the 128-character limit rejected it first.
        mockMvc.perform(resetPassword("t".repeat(129), USER_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "The information you entered is incomplete or invalid. Please check it and try again."));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedStudent() throws Exception {
        String email = uniqueEmail("me-student");
        String token = api.registerStudent(email);

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.studentProfile.totalPoints").value(0))
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

        assertThat(activeRefreshTokenIds(user(email))).isEmpty();

        mockMvc.perform(api.authGet(ME_PATH, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * Account › This device: "Sign out" ends this browser's session only (its access token and refresh cookie); the
     * account's other devices stay signed in. The session says when this browser signed in.
     */
    @Test
    void signingOutOfOneBrowserKeepsTheOtherDevicesSignedIn() throws Exception {
        String email = uniqueEmail("logout-one-device");
        api.registerStudentAccount(email);
        MvcResult laptop = login(email);
        MvcResult phone = login(email);
        String laptopToken = accessToken(laptop);

        mockMvc.perform(api.authGet("/api/v1/users/me/session", laptopToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signedInAt").isString());

        mockMvc.perform(api.authPost(LOGOUT_PATH, laptopToken))
                .andExpect(status().isOk());

        mockMvc.perform(api.authGet(ME_PATH, laptopToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(refreshCookieValue(laptop))).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet(ME_PATH, accessToken(phone))).andExpect(status().isOk());
        mockMvc.perform(refresh(refreshCookieValue(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
        assertThat(activeRefreshTokenIds(user(email))).hasSize(1);
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
                .contains("\"studentProfile\":null")
                .doesNotContain("teacherProfile", "passwordHash", "password_hash");
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
        // An unknown token gets the general error; a used or an expired link says so (see the tests below).
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
    void changingThePasswordRetiresPreviouslyIssuedRecoveryLinks() throws Exception {
        String email = uniqueEmail("change-recovery");
        String session = api.registerStudent(email);
        String clientIp = nextClientIp();
        String link = issueResetToken(email, clientIp);

        mockMvc.perform(api.changePasswordRequest(session, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());
        mockMvc.perform(api.jsonPost(RESET_PASSWORD_PATH + "/check", null, Map.of("token", link))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isBadRequest());
        String fresh = issueResetToken(email, clientIp);
        mockMvc.perform(api.jsonPost(RESET_PASSWORD_PATH + "/check", null, Map.of("token", fresh))
                        .with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
    }

    @Test
    void shouldCompletePasswordResetEndToEndAndAllowLoginWithNewPasswordExactlyOnce() throws Exception {
        String email = uniqueEmail("reset-flow");
        api.registerStudentAccount(email);
        String originalToken = api.login(email, USER_PASSWORD);
        String originalRefreshToken = refreshCookieValue(login(email));

        String rawResetToken = issueResetToken(email);

        User user = user(email);
        PasswordResetToken token = passwordResetTokenRepository.findById(activeResetTokenIds(user).getFirst()).orElseThrow();
        // 60-minute lifetime (SDD 5.1).
        assertThat(token.getExpiresAt()).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusMinutes(61));
        assertThat(token.getTokenHash()).isNotBlank().isNotEqualTo(rawResetToken);

        mockMvc.perform(resetPassword(rawResetToken, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(activeRefreshTokenIds(user)).isEmpty();

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
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Reset token has already been used."));
    }

    @Test
    void shouldRejectExpiredPasswordResetTokenAndKeepTheOldPassword() throws Exception {
        // FRAUTH09: a reset link past its expiry time cannot change the password.
        String email = uniqueEmail("reset-expired");
        api.registerStudentAccount(email);
        String rawResetToken = issueResetToken(email);

        Long tokenId = activeResetTokenIds(user(email)).getFirst();
        // expires_at is not updatable through JPA, so age the token directly in the table (bound as a Timestamp,
        // as Hibernate binds it, so the connection's time-zone conversion applies).
        int aged = jdbc.update(
                "update password_reset_tokens set expires_at = ? where id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                tokenId);
        assertThat(aged).isEqualTo(1);

        mockMvc.perform(resetPassword(rawResetToken, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Reset token has expired. Please request a new one."));

        assertThat(passwordResetTokenRepository.findById(tokenId).orElseThrow().getUsedAt()).isNull();
        assertThat(api.login(email, USER_PASSWORD)).isNotBlank();
        mockMvc.perform(api.loginRequest(email, NEW_PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIssueFreshTokenAndInvalidatePreviousActiveTokenWhenForgotPasswordIsCalledTwice() throws Exception {
        // A newer reset request invalidates older still-active links.
        String email = uniqueEmail("reset-rotate");
        api.registerStudentAccount(email);

        String firstRawToken = issueResetToken(email);
        String secondRawToken = issueResetToken(email);
        assertThat(secondRawToken).isNotEqualTo(firstRawToken);

        // The original token is marked used and only the newer token remains active.
        assertThat(activeResetTokenIds(user(email))).hasSize(1);

        mockMvc.perform(resetPassword(firstRawToken, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldConsumePasswordResetTokenOnlyOnceAtRepositoryBoundary() throws Exception {
        String email = uniqueEmail("reset-atomic");
        api.registerStudentAccount(email);
        issueResetToken(email);

        Long tokenId = activeResetTokenIds(user(email)).getFirst();
        LocalDateTime consumeAt = LocalDateTime.now();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Integer firstConsume = transaction.execute(status -> passwordResetTokenRepository.consumeActiveToken(tokenId, consumeAt));
        Integer replayConsume = transaction.execute(status -> passwordResetTokenRepository.consumeActiveToken(tokenId, LocalDateTime.now()));

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
        api.registerStudentAccount(registeredEmail);

        MvcResult existingResult = mockMvc.perform(forgotPassword(registeredEmail).with(fromRemoteAddress(nextClientIp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        MvcResult missingResult = mockMvc.perform(forgotPassword(uniqueEmail("definitely-not-registered"))
                        .with(fromRemoteAddress(nextClientIp())))
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
        // Fresh email per request so only the per-IP bucket triggers the 429; loopback remote trusts X-Forwarded-For.
        String forwardedIp = nextClientIp();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(forgotPassword(uniqueEmail("forgot-limit")).header("X-Forwarded-For", forwardedIp))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(forgotPassword(uniqueEmail("forgot-limit")).header("X-Forwarded-For", forwardedIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromUntrustedRemoteAddress() throws Exception {
        // A new e-mail for every request, so only the per-IP bucket can trigger the 429 below: if the spoofed
        // header were trusted, the first ten requests would count against the spoofed IP instead.
        String actualRemoteIp = nextClientIp();
        String spoofedForwardedIp = nextClientIp();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(forgotPassword(uniqueEmail("forgot-untrusted"))
                            .with(fromRemoteAddress(actualRemoteIp))
                            .header("X-Forwarded-For", spoofedForwardedIp))
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
        // Brute-force defence (SDD 3.4): five failed attempts lock sign-in for 15 minutes, and further attempts are
        // refused with 429 before the password is checked. A fresh unique account keeps the limiter bucket
        // independent of the other tests in this JVM.
        String email = uniqueEmail("lockout-target");
        api.registerStudentAccount(email);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult locked = mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn();
        assertThat(Long.parseLong(locked.getResponse().getHeader("Retry-After"))).isBetween(14 * 60L, 15 * 60L);

        // Even the correct password is refused while the bucket is locked.
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * The lockout notice tells the user to reset the password, and the reset page then says to sign in with the new
     * one: on the device that reset it, that sign-in works at once.
     */
    @Test
    void passwordResetLiftsTheSignInPauseOfTheDeviceThatReset() throws Exception {
        String email = uniqueEmail("reset-lockout");
        api.registerStudentAccount(email);
        String clientIp = nextClientIp();
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD).with(fromRemoteAddress(clientIp)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(api.loginRequest(email, USER_PASSWORD).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isTooManyRequests());

        String rawResetToken = issueResetToken(email, clientIp);
        mockMvc.perform(resetPassword(rawResetToken, NEW_PASSWORD).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk());

        mockMvc.perform(api.loginRequest(email, NEW_PASSWORD).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void loginLockoutAuditRecordShouldNotStoreEmailOrIpAddress() throws Exception {
        String email = uniqueEmail("lockout-audit");
        api.registerStudentAccount(email);
        String clientIp = nextClientIp();
        String requestId = UUID.randomUUID().toString();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(api.loginRequest(email, WRONG_PASSWORD)
                            .with(fromRemoteAddress(clientIp)).header("X-Request-Id", requestId + "-" + attempt))
                    .andExpect(status().isUnauthorized());
        }

        AuditLog lockoutAudit = auditForRequest(requestId + "-5", "LOGIN_LOCKOUT").orElseThrow();

        assertThat(lockoutAudit.getTargetType()).isEqualTo("User");
        assertThat(lockoutAudit.getTargetId()).isNull();
        assertThat(lockoutAudit.getDescription()).contains("repeated failed attempts").doesNotContain(email, clientIp);
    }

    /** The teacher sign-up as the earlier version of the site sent it. */
    private MockHttpServletRequestBuilder teacherRegistration(String email) throws Exception {
        return api.jsonPost(REGISTER_TEACHER_PATH, null, Map.of(
                "fullName", "Teacher One",
                "email", email,
                "password", USER_PASSWORD,
                "confirmPassword", USER_PASSWORD,
                "bio", "Friendly Mandarin teacher with ten years of classes."));
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Registers a student, signs in and returns the refresh-cookie value of that sign-in. */
    private String signedInRefreshToken(String emailPrefix) throws Exception {
        String email = uniqueEmail(emailPrefix);
        api.registerStudentAccount(email);
        return refreshCookieValue(login(email));
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
                "confirmPassword", password)).with(fromRemoteAddress(nextClientIp()));
    }

    private List<Long> activeRefreshTokenIds(User user) {
        return jdbc.queryForList("select id from refresh_tokens where user_id = ? and revoked_at is null",
                Long.class, user.getId());
    }

    private List<Long> activeResetTokenIds(User user) {
        return jdbc.queryForList("select id from password_reset_tokens where user_id = ? and used_at is null",
                Long.class, user.getId());
    }

    private String accessToken(MvcResult result) throws Exception {
        return api.data(result).path("token").asText();
    }

    private String refreshCookieValue(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE_NAME);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    private void assertRefreshCookieIssued(MvcResult result) {
        assertThat(refreshCookieHeader(result))
                .contains(REFRESH_COOKIE_PREFIX, "HttpOnly", "SameSite=Strict")
                .doesNotContain("Max-Age=0");
    }

    private void assertRefreshCookieCleared(MvcResult result) {
        assertThat(refreshCookieHeader(result)).contains(REFRESH_COOKIE_PREFIX + ";", "Max-Age=0");
    }

    /** The raw Set-Cookie header of the refresh cookie, which must be limited to the refresh path. */
    private String refreshCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(SET_COOKIE_HEADER).stream()
                .filter(cookie -> cookie.contains(REFRESH_COOKIE_PREFIX))
                .filter(cookie -> cookie.contains("; Path=" + REFRESH_PATH + ";"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing refresh cookie for path " + REFRESH_PATH));
    }

    private String issueResetToken(String email) throws Exception {
        return issueResetToken(email, nextClientIp());
    }

    /** Asks for a reset link from {@code clientIp} and returns the token of the link e-mailed to {@code email}. */
    private String issueResetToken(String email, String clientIp) throws Exception {
        long before = events.stream(EmailDeliveryRequested.class)
                .filter(event -> email.equals(event.email()) && "Password reset request".equals(event.subject()))
                .count();
        mockMvc.perform(forgotPassword(email).with(fromRemoteAddress(clientIp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<EmailDeliveryRequested> resetEmails = events.stream(EmailDeliveryRequested.class)
                .filter(event -> email.equals(event.email()) && "Password reset request".equals(event.subject()))
                .toList();
        assertThat(resetEmails)
                .as("a new password reset email event should be published for %s", email)
                .hasSize(Math.toIntExact(before + 1));

        Matcher matcher = RESET_TOKEN_PATTERN.matcher(resetEmails.getLast().body());
        assertThat(matcher.find()).as("the email body should hold the reset link").isTrue();
        return matcher.group(1);
    }

    private static MockCookie refreshCookie(String token) {
        return new MockCookie(REFRESH_COOKIE_NAME, token);
    }
}
