package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.PasswordResetToken;
import com.example.aichinese.entity.RefreshToken;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.NoOpEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 *   <li>Teacher accounts start PENDING; they can sign in and the
 *       login response carries {@code teacherStatus}.</li>
 *   <li>Logout bumps the {@code sessionVersion} so existing JWTs are
 *       revoked instantly.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.security.forwarded.trusted-proxies[0]=198.51.100.0/24"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTests {

    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("reset form:\\s*([A-Za-z0-9]+)");
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final String REFRESH_COOKIE_NAME = "mandarin_spark_refresh";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CACHE_CONTROL_HEADER = "Cache-Control";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_STUDENT_PATH = "/api/v1/auth/register/student";
    private static final String REGISTER_TEACHER_PATH = "/api/v1/auth/register/teacher";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String ME_PATH = "/api/v1/auth/me";
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    private static final String RESET_PASSWORD_PATH = "/api/v1/auth/reset-password";
    private static final String NO_STORE = "no-store";
    private static final String LEGACY_REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoSpyBean
    private NoOpEmailService noOpEmailService;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {
        String email = uniqueEmail("student");

        MvcResult result = mockMvc.perform(post(REGISTER_STUDENT_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Student One",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "ms"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        User user = userByEmail(email);

        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(studentProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRegisterTeacherSuccessfullyWithPendingStatus() throws Exception {
        String email = uniqueEmail("teacher");

        mockMvc.perform(post(REGISTER_TEACHER_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Teacher One",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Friendly Mandarin teacher"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.teacherStatus").value("PENDING"));

        User user = userByEmail(email);
        TeacherProfile profile = teacherProfileRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(profile.getTeacherStatus()).isEqualTo(TeacherStatus.PENDING);
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = uniqueEmail("duplicate");

        registerStudent(email, USER_PASSWORD);

        mockMvc.perform(post(REGISTER_STUDENT_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Student Duplicate",
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }

    @Test
    void shouldLoginSuccessfullyForStudent() throws Exception {
        String email = uniqueEmail("login-student");
        registerStudent(email, USER_PASSWORD);
        String requestId = "req-login-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", USER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL_HEADER, containsString(NO_STORE)))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(refreshCookiePrefix())))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("HttpOnly")))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("SameSite=Strict")))
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
        assertRefreshCookieIssuedAndLegacyCookieCleared(result);
    }

    @Test
    void shouldRefreshAccessTokenRotateRefreshCookieAndRejectReplay() throws Exception {
        String email = uniqueEmail("refresh-rotate");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String initialAccessToken = extractToken(loginResult);
        String initialRefreshToken = extractRefreshCookieValue(loginResult);

        MvcResult refreshResult = mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(initialRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(CACHE_CONTROL_HEADER, containsString(NO_STORE)))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(refreshCookiePrefix())))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String rotatedAccessToken = extractToken(refreshResult);
        String rotatedRefreshToken = extractRefreshCookieValue(refreshResult);
        assertThat(rotatedAccessToken).isNotEqualTo(initialAccessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);
        assertRefreshCookieIssuedAndLegacyCookieCleared(refreshResult);

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(rotatedAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(initialRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(rotatedAccessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectCrossSiteBrowserRefreshWithoutConsumingRefreshToken() throws Exception {
        String email = uniqueEmail("refresh-origin");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .header("Origin", "https://evil.example")
                .header("Sec-Fetch-Site", "cross-site")
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(header().string(CACHE_CONTROL_HEADER, containsString(NO_STORE)))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldRejectSameSiteBrowserRefreshWithoutTrustedOriginMetadata() throws Exception {
        String email = uniqueEmail("refresh-same-site");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .header("Sec-Fetch-Site", "same-site")
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowSameOriginBrowserRefreshWithoutOriginOrReferer() throws Exception {
        String email = uniqueEmail("refresh-same-origin-fetch");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .header("Sec-Fetch-Site", "same-origin")
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowRefreshFromConfiguredFrontendOrigin() throws Exception {
        String email = uniqueEmail("refresh-allowed-origin");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .header("Origin", "http://localhost:5173")
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldClearRefreshCookieAndRevokeRefreshTokensOnLogout() throws Exception {
        String email = uniqueEmail("refresh-logout");
        registerStudent(email, USER_PASSWORD);

        MvcResult loginResult = login(email, USER_PASSWORD);
        String accessToken = extractToken(loginResult);
        String refreshToken = extractRefreshCookieValue(loginResult);
        User user = userByEmail(email);
        RefreshToken issuedRefreshToken = activeRefreshTokenFor(user);
        assertThat(issuedRefreshToken.getUpdatedAt()).isNotNull();

        MvcResult logoutResult = mockMvc.perform(post(LOGOUT_PATH)
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(accessToken))
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(SET_COOKIE_HEADER, containsString(refreshCookiePrefix() + ";")))
                .andExpect(header().string(SET_COOKIE_HEADER, containsString("Max-Age=0")))
                .andReturn();

        assertRefreshCookieCleared(logoutResult, REFRESH_COOKIE_PATH);
        assertRefreshCookieCleared(logoutResult, LEGACY_REFRESH_COOKIE_PATH);

        var revokedRefreshToken = refreshTokenRepository.findById(Objects.requireNonNull(issuedRefreshToken.getId()))
                .orElseThrow();
        assertThat(revokedRefreshToken.getRevokedAt()).isNotNull();
        assertThat(revokedRefreshToken.getUpdatedAt()).isEqualTo(revokedRefreshToken.getRevokedAt());
        assertThat(activeRefreshTokenCountFor(user)).isZero();

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void forgotPasswordAuditLogShouldCaptureRequestTraceId() throws Exception {
        String email = uniqueEmail("forgot-trace");
        registerStudent(email, USER_PASSWORD);
        String requestId = "req-forgot-" + UUID.randomUUID();
        long maxAuditIdBefore = maxAuditId();

        mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true));

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(item -> item.getId() != null && item.getId() > maxAuditIdBefore)
                .filter(item -> "PASSWORD_RESET_REQUESTED".equals(item.getActionType()))
                .findFirst()
                .orElseThrow();

        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        String email = uniqueEmail("wrong-password");
        registerStudent(email, USER_PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", "WrongPassword999!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        // An unregistered email gets the same general message, so accounts cannot be enumerated (T02).
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", uniqueEmail("never-registered"),
                        "password", "WrongPassword999!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void authEndpointsShouldRejectOversizedBoundaryInputs() throws Exception {
        String oversizedEmail = "x".repeat(321);
        String oversizedPassword = "P".repeat(73);

        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", "student@example.com",
                        "password", oversizedPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", oversizedEmail))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post(REGISTER_STUDENT_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Boundary Student",
                        "email", "boundary-student@example.com",
                        "password", USER_PASSWORD,
                        "confirmPassword", oversizedPassword,
                        "preferredLanguage", "en"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", "t".repeat(129),
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldAllowAdminLoginFromSeededAccount() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", ADMIN_EMAIL,
                        "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedStudent() throws Exception {
        String email = uniqueEmail("me-student");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.studentProfile").isMap())
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                // Prevent profile replay from browser cache after logout.
                .andExpect(header().string(CACHE_CONTROL_HEADER, containsString(NO_STORE)));
    }

    @Test
    void shouldRejectExistingTokenAfterUserIsDeactivated() throws Exception {
        String email = uniqueEmail("inactive-token");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        User user = userByEmail(email);
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectExistingTokenAfterExplicitLogout() throws Exception {
        String email = uniqueEmail("logout-token");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);
        User user = userByEmail(email);

        mockMvc.perform(post(LOGOUT_PATH)
                .with(csrf())
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(activeRefreshTokenCountFor(user)).isZero();

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedTeacher() throws Exception {
        String email = uniqueEmail("me-teacher");
        registerTeacher(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherProfile").isMap())
                .andExpect(jsonPath("$.data.teacherProfile.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.studentProfile").doesNotExist());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedAdminWithoutProfiles() throws Exception {
        String token = loginAndExtractToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("\"studentProfile\":null");
        assertThat(responseBody).contains("\"teacherProfile\":null");
        assertThat(responseBody).doesNotContain("passwordHash");
        assertThat(responseBody).doesNotContain("password_hash");
    }

    @Test
    void shouldRejectProtectedRouteWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/students/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesAdminRoute() throws Exception {
        String email = uniqueEmail("forbidden-student");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action. Contact an administrator if needed."));
    }

    @Test
    void shouldReturnForbiddenWhenTeacherAccessesAdminRoute() throws Exception {
        String email = uniqueEmail("forbidden-teacher");
        registerTeacher(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesTeacherRoute() throws Exception {
        String email = uniqueEmail("student-on-teacher");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIncludeTeacherStatusWhenTeacherLogsIn() throws Exception {
        String email = uniqueEmail("teacher-login");
        registerTeacher(email, USER_PASSWORD);

        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", USER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.user.teacherStatus").value("PENDING"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("could not be processed");
        assertThat(responseBody).doesNotContain("Exception");
    }

    @Test
    void shouldReturnBadRequestWhenPathVariableTypeDoesNotMatch() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/teachers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("The request could not be processed. Please check the input and try again."))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at org.springframework");
    }

    @Test
    void shouldReturnNotFoundForUnknownAuthenticatedApiRoute() throws Exception {
        // After the default-deny SecurityConfig change, an unmapped path is only
        // reachable when the request is authenticated. We therefore log in as a student
        // and hit a non-existent path under the student namespace so that Spring Security
        // permits the request and the GlobalExceptionHandler can handle the resulting
        // NoResourceFoundException with a clean 404 response.
        String email = uniqueEmail("not-found");
        registerStudent(email, USER_PASSWORD);
        String token = loginAndExtractToken(email, USER_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/v1/students/this-route-does-not-exist-" + UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("We could not find the item you requested. Please refresh and try again."))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Exception");
        assertThat(responseBody).doesNotContain("at org.springframework");
    }

    @Test
    void shouldRejectResetPasswordWithUnknownToken() throws Exception {
        // Unknown and already-used reset tokens intentionally share the same public error.
        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", "not-a-real-token-" + UUID.randomUUID(),
                        "password", "NewPassword123!",
                        "confirmPassword", "NewPassword123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("invalid")));
    }

    @Test
    void shouldRejectResetPasswordWithBlankToken() throws Exception {
        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", "   ",
                        "password", "NewPassword123!",
                        "confirmPassword", "NewPassword123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldCompletePasswordResetEndToEndAndAllowLoginWithNewPasswordExactlyOnce() throws Exception {
        // End-to-end reset flow plus single-use enforcement.
        String email = uniqueEmail("reset-flow");
        String oldPassword = USER_PASSWORD;
        String newPassword = "NewPassword456!";
        registerStudent(email, oldPassword);
        String originalToken = loginAndExtractToken(email, oldPassword);
        MvcResult originalSession = login(email, oldPassword);
        String originalRefreshToken = extractRefreshCookieValue(originalSession);

        String rawResetToken = issueResetToken(email);

        User user = userByEmail(email);
        PasswordResetToken token = activePasswordResetTokenFor(user);
        // 60-minute lifetime (SDD 5.1).
        assertThat(token.getExpiresAt()).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusMinutes(61));
        assertThat(token.getTokenHash()).isNotBlank().isNotEqualTo(rawResetToken);

        // First use of the token: must succeed and update the stored hash.
        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", rawResetToken,
                        "password", newPassword,
                        "confirmPassword", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(activeRefreshTokenCountFor(user)).isZero();

        mockMvc.perform(post(REFRESH_PATH)
                .with(csrf())
                .cookie(refreshCookie(originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // The old password must no longer work; the new password must.
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", oldPassword))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        mockMvc.perform(get(ME_PATH)
                .header(AUTHORIZATION_HEADER, bearer(originalToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Single-use enforcement: the same token cannot be redeemed a second time.
        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", rawResetToken,
                        "password", "AnotherNew789!",
                        "confirmPassword", "AnotherNew789!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectExpiredPasswordResetTokenAndKeepTheOldPassword() throws Exception {
        // FR-AUTH-15: a reset link past its expiry time cannot change the password.
        String email = uniqueEmail("reset-expired");
        registerStudent(email, USER_PASSWORD);
        String rawResetToken = issueResetToken(email);

        User user = userByEmail(email);
        PasswordResetToken token = activePasswordResetTokenFor(user);
        // expires_at is not updatable through JPA, so age the token directly in the table.
        int aged = jdbcTemplate.update(
                "update password_reset_tokens set expires_at = ? where id = ?",
                LocalDateTime.now().minusMinutes(1),
                token.getId());
        assertThat(aged).isEqualTo(1);

        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", rawResetToken,
                        "password", "NewPassword456!",
                        "confirmPassword", "NewPassword456!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(passwordResetTokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNull();
        assertThat(loginAndExtractToken(email, USER_PASSWORD)).isNotBlank();
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", "NewPassword456!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIssueFreshTokenAndInvalidatePreviousActiveTokenWhenForgotPasswordIsCalledTwice() throws Exception {
        // A newer reset request invalidates older still-active links.
        String email = uniqueEmail("reset-rotate");
        registerStudent(email, USER_PASSWORD);

        String firstRawToken = issueResetToken(email);

        User user = userByEmail(email);
        PasswordResetToken firstToken = passwordResetTokenFor(user);
        assertThat(firstToken.getTokenHash()).isNotBlank().isNotEqualTo(firstRawToken);

        String secondRawToken = issueResetToken(email);
        assertThat(secondRawToken).isNotEqualTo(firstRawToken);

        // The original token is marked used and only the newer token remains active.
        long activeTokens = activePasswordResetTokenCountFor(user);
        assertThat(activeTokens).isEqualTo(1L);

        mockMvc.perform(post(RESET_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "token", firstRawToken,
                        "password", "NewPassword456!",
                        "confirmPassword", "NewPassword456!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldConsumePasswordResetTokenOnlyOnceAtRepositoryBoundary() throws Exception {
        String email = uniqueEmail("reset-atomic");
        registerStudent(email, USER_PASSWORD);
        issueResetToken(email);

        User user = userByEmail(email);
        PasswordResetToken token = activePasswordResetTokenFor(user);
        Long tokenId = Objects.requireNonNull(token.getId());
        LocalDateTime consumeAt = LocalDateTime.now();

        int firstConsume = passwordResetTokenRepository.consumeActiveToken(
                tokenId,
                consumeAt);
        int replayConsume = passwordResetTokenRepository.consumeActiveToken(
                tokenId,
                LocalDateTime.now());

        assertThat(firstConsume).isEqualTo(1);
        assertThat(replayConsume).isZero();
        PasswordResetToken consumedToken = passwordResetTokenRepository.findById(tokenId).orElseThrow();
        assertThat(consumedToken.getUsedAt()).isEqualTo(consumedToken.getUpdatedAt());
        assertThat(Math.abs(ChronoUnit.MICROS.between(consumeAt, consumedToken.getUsedAt())))
                .isLessThanOrEqualTo(1L);
    }

    @Test
    void shouldReturnConsistentForgotPasswordResponseRegardlessOfEmailExistence() throws Exception {
        // Register a real account first so we can compare responses for a known-existing
        // email and a definitely-non-existent email. The endpoint must return the same
        // public message in both cases so attackers cannot probe which addresses are
        // registered on the platform.
        String registeredEmail = uniqueEmail("forgot-existing");
        registerStudent(registeredEmail, USER_PASSWORD);

        MvcResult existingResult = mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", registeredEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MvcResult missingResult = mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", "definitely-not-registered-" + UUID.randomUUID() + "@example.com"))))
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
        String email = "forgot-limit-" + UUID.randomUUID() + "@example.com";
        String forwardedIp = "198.51.100.200";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                    .with(csrf())
                    .header("X-Forwarded-For", forwardedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .header("X-Forwarded-For", forwardedIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromUntrustedRemoteAddress() throws Exception {
        String email = "forgot-untrusted-" + UUID.randomUUID() + "@example.com";
        String spoofedClientIp = "203.0.113.55";
        String actualRemoteIp = "203.0.113.200";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(actualRemoteIp);
                        return request;
                    })
                    .header("X-Forwarded-For", spoofedClientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(actualRemoteIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnUnauthorizedForUnmappedApiRouteWithoutToken() throws Exception {
        // Default-deny check: if a request path that is not explicitly listed in
        // SecurityConfig is reached without authentication, the application must
        // respond with 401, not silently allow the request through.
        mockMvc.perform(get("/api/v1/some-future-route-without-explicit-rule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldLockOutLoginAfterRepeatedFailedAttempts() throws Exception {
        // Verify the brute-force defence: after the LoginAttemptLimiter
        // threshold (5 by default) is reached, further attempts must be
        // refused with HTTP 429 BEFORE the password is even checked. We use
        // a freshly registered student so this test is independent of the
        // shared seeded admin account, and a unique email so other tests in
        // the same JVM cannot poison the limiter's bucket.
        String email = uniqueEmail("lockout-target");
        registerStudent(email, USER_PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(LOGIN_PATH)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                            "email", email,
                            "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized());
        }

        // Sixth attempt, still wrong password, should be refused at the
        // rate-limiter layer with 429 Too Many Requests, not 401.
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", "WrongPassword!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));

        // And critically: even the *correct* password is refused while the
        // bucket is locked, otherwise an attacker who finally guesses right
        // would still slip through after the threshold.
        mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", USER_PASSWORD))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginLockoutAuditRecordShouldNotStoreEmailOrIpAddress() throws Exception {
        String email = uniqueEmail("lockout-audit");
        String forwardedIp = "198.51.100.44";
        registerStudent(email, USER_PASSWORD);
        long maxAuditIdBefore = maxAuditId();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(LOGIN_PATH)
                    .with(csrf())
                    .header("X-Forwarded-For", forwardedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of(
                            "email", email,
                            "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized());
        }

        var lockoutAudit = auditLogRepository.findAll().stream()
                .filter(auditLog -> auditLog.getId() != null && auditLog.getId() > maxAuditIdBefore)
                .filter(auditLog -> "LOGIN_LOCKOUT".equals(auditLog.getActionType()))
                .filter(auditLog -> "USER".equals(auditLog.getTargetType()))
                .filter(auditLog -> auditLog.getDescription() != null
                        && auditLog.getDescription().contains("repeated failed attempts"))
                .findFirst()
                .orElseThrow();

        assertThat(lockoutAudit.getDescription()).doesNotContain(email);
        assertThat(lockoutAudit.getIpAddress()).isNull();
    }

    private void registerStudent(String email, String password) throws Exception {
        mockMvc.perform(post(REGISTER_STUDENT_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Student Test",
                        "email", email,
                        "password", password,
                        "confirmPassword", password,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());
    }

    private void registerTeacher(String email, String password) throws Exception {
        mockMvc.perform(post(REGISTER_TEACHER_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Teacher Test",
                        "email", email,
                        "password", password,
                        "confirmPassword", password,
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = login(email, password);

        return extractToken(result);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String json(Map<String, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private User userByEmail(String email) {
        return userRepository.findByEmailWithRoles(email).orElseThrow();
    }

    private RefreshToken activeRefreshTokenFor(User user) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> belongsTo(token.getUser(), user))
                .filter(token -> token.getRevokedAt() == null)
                .findFirst()
                .orElseThrow();
    }

    private long activeRefreshTokenCountFor(User user) {
        return refreshTokenRepository.findAll().stream()
                .filter(token -> belongsTo(token.getUser(), user))
                .filter(token -> token.getRevokedAt() == null)
                .count();
    }

    private PasswordResetToken activePasswordResetTokenFor(User user) {
        return passwordResetTokenRepository.findAll().stream()
                .filter(token -> belongsTo(token.getUser(), user))
                .filter(token -> token.getUsedAt() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Reset token was not persisted for the registered email."));
    }

    private PasswordResetToken passwordResetTokenFor(User user) {
        return passwordResetTokenRepository.findAll().stream()
                .filter(token -> belongsTo(token.getUser(), user))
                .findFirst()
                .orElseThrow();
    }

    private long activePasswordResetTokenCountFor(User user) {
        return passwordResetTokenRepository.findAll().stream()
                .filter(token -> belongsTo(token.getUser(), user))
                .filter(token -> token.getUsedAt() == null)
                .count();
    }

    private long maxAuditId() {
        return auditLogRepository.findAll().stream()
                .mapToLong(auditLog -> auditLog.getId() == null ? 0L : auditLog.getId())
                .max()
                .orElse(0L);
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private String extractRefreshCookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(SET_COOKIE_HEADER);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(refreshCookiePrefix());
        String cookieValue = setCookie.substring(refreshCookiePrefix().length());
        int separatorIndex = cookieValue.indexOf(';');
        return separatorIndex >= 0 ? cookieValue.substring(0, separatorIndex) : cookieValue;
    }

    private void assertRefreshCookieIssuedAndLegacyCookieCleared(MvcResult result) {
        String refreshCookie = assertRefreshCookieWithPath(result, REFRESH_COOKIE_PATH);
        assertThat(refreshCookie)
                .contains(refreshCookiePrefix(), "HttpOnly", "SameSite=Strict")
                .doesNotContain("Max-Age=0");
        assertRefreshCookieCleared(result, LEGACY_REFRESH_COOKIE_PATH);
    }

    private void assertRefreshCookieCleared(MvcResult result, String path) {
        assertThat(assertRefreshCookieWithPath(result, path))
                .contains(refreshCookiePrefix() + ";", "Max-Age=0");
    }

    private String assertRefreshCookieWithPath(MvcResult result, String path) {
        return result.getResponse().getHeaders(SET_COOKIE_HEADER).stream()
                .filter(cookie -> cookie.contains(refreshCookiePrefix()))
                .filter(cookie -> cookie.contains("; Path=" + path + ";"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing refresh cookie for path " + path));
    }

    private String issueResetToken(String email) throws Exception {
        clearInvocations(noOpEmailService);

        mockMvc.perform(post(FORGOT_PASSWORD_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<User> recipientCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(noOpEmailService, timeout(2_000).atLeastOnce()).sendEmail(
                recipientCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        User recipient = recipientCaptor.getAllValues().get(recipientCaptor.getAllValues().size() - 1);
        String body = bodyCaptor.getAllValues().get(bodyCaptor.getAllValues().size() - 1);
        assertThat(recipient).isNotNull();
        assertThat(recipient.getEmail()).isEqualTo(email);

        Matcher matcher = RESET_TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).as("reset token should appear in the fallback email body").isTrue();
        return matcher.group(1);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static MockCookie refreshCookie(String token) {
        return new MockCookie(REFRESH_COOKIE_NAME, token);
    }

    private static String refreshCookiePrefix() {
        return REFRESH_COOKIE_NAME + "=";
    }

    private static boolean belongsTo(User candidate, User user) {
        return candidate != null && candidate.getId().equals(user.getId());
    }
}
