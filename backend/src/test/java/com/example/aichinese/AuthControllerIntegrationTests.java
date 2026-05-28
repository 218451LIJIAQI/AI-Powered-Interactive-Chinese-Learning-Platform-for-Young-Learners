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

import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
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
 *   <li>Register / forgot-password 429 via
 *       {@code PublicAuthRequestLimiter}.</li>
 *   <li>Forgot-password response is identical whether or not the
 *       email exists.</li>
 *   <li>Reset tokens are single-use, 60-minute TTL, only the hash is
 *       stored.</li>
 *   <li>{@code /me} carries {@code Cache-Control: no-store}.</li>
 *   <li>Teacher accounts start PENDING and cannot log in until
 *       approved.</li>
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
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoSpyBean
    private NoOpEmailService noOpEmailService;

    @Test
    void shouldRegisterStudentSuccessfully() throws Exception {
        String email = uniqueEmail("student");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student One",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "ms"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();

        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(studentProfileRepository.findByUserId(user.getId())).isPresent();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRegisterTeacherSuccessfullyWithPendingStatus() throws Exception {
        String email = uniqueEmail("teacher");

        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher One",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Friendly Mandarin teacher"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.teacherStatus").value("PENDING"));

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        TeacherProfile profile = teacherProfileRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(profile.getTeacherStatus()).isEqualTo(TeacherStatus.PENDING);
    }

    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String email = uniqueEmail("duplicate");

        registerStudent(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Duplicate",
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email is already registered."));
    }

    @Test
    void shouldLoginSuccessfullyForStudent() throws Exception {
        String email = uniqueEmail("login-student");
        registerStudent(email, "Password123!");
        String requestId = "req-login-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Set-Cookie", containsString("mandarin_spark_refresh=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("STUDENT"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("password_hash");
    }

    @Test
    void shouldRefreshAccessTokenRotateRefreshCookieAndRejectReplay() throws Exception {
        String email = uniqueEmail("refresh-rotate");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String initialAccessToken = extractToken(loginResult);
        String initialRefreshToken = extractRefreshCookieValue(loginResult);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", initialRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Set-Cookie", containsString("mandarin_spark_refresh=")))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        String rotatedAccessToken = extractToken(refreshResult);
        String rotatedRefreshToken = extractRefreshCookieValue(refreshResult);
        assertThat(rotatedAccessToken).isNotEqualTo(initialAccessToken);
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + rotatedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", initialRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + rotatedAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectCrossSiteBrowserRefreshWithoutConsumingRefreshToken() throws Exception {
        String email = uniqueEmail("refresh-origin");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .header("Origin", "https://evil.example")
                .header("Sec-Fetch-Site", "cross-site")
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldRejectSameSiteBrowserRefreshWithoutTrustedOriginMetadata() throws Exception {
        String email = uniqueEmail("refresh-same-site");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .header("Sec-Fetch-Site", "same-site")
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowSameOriginBrowserRefreshWithoutOriginOrReferer() throws Exception {
        String email = uniqueEmail("refresh-same-origin-fetch");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .header("Sec-Fetch-Site", "same-origin")
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldAllowRefreshFromConfiguredFrontendOrigin() throws Exception {
        String email = uniqueEmail("refresh-allowed-origin");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String refreshToken = extractRefreshCookieValue(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .header("Origin", "http://localhost:5173")
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void shouldClearRefreshCookieAndRevokeRefreshTokensOnLogout() throws Exception {
        String email = uniqueEmail("refresh-logout");
        registerStudent(email, "Password123!");

        MvcResult loginResult = login(email, "Password123!");
        String accessToken = extractToken(loginResult);
        String refreshToken = extractRefreshCookieValue(loginResult);
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        var issuedRefreshToken = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser() != null && token.getUser().getId().equals(user.getId()))
                .filter(token -> token.getRevokedAt() == null)
                .findFirst()
                .orElseThrow();
        assertThat(issuedRefreshToken.getUpdatedAt()).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(csrf())
                .header("Authorization", "Bearer " + accessToken)
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("mandarin_spark_refresh=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        var revokedRefreshToken = refreshTokenRepository.findById(Objects.requireNonNull(issuedRefreshToken.getId()))
                .orElseThrow();
        assertThat(revokedRefreshToken.getRevokedAt()).isNotNull();
        assertThat(revokedRefreshToken.getUpdatedAt()).isEqualTo(revokedRefreshToken.getRevokedAt());
        assertThat(refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser() != null && token.getUser().getId().equals(user.getId()))
                .filter(token -> token.getRevokedAt() == null)
                .count()).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void forgotPasswordAuditLogShouldCaptureRequestTraceId() throws Exception {
        String email = uniqueEmail("forgot-trace");
        registerStudent(email, "Password123!");
        String requestId = "req-forgot-" + UUID.randomUUID();
        long maxAuditIdBefore = auditLogRepository.findAll().stream()
                .mapToLong(auditLog -> auditLog.getId() == null ? 0L : auditLog.getId())
                .max()
                .orElse(0L);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
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
        registerStudent(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "WrongPassword999!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void authEndpointsShouldRejectOversizedBoundaryInputs() throws Exception {
        String oversizedEmail = "x".repeat(321);
        String oversizedPassword = "P".repeat(73);

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "student@example.com",
                        "password", oversizedPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", oversizedEmail))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Boundary Student",
                        "email", "boundary-student@example.com",
                        "password", "Password123!",
                        "confirmPassword", oversizedPassword,
                        "preferredLanguage", "en"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "t".repeat(129),
                        "password", "Password123!",
                        "confirmPassword", "Password123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldAllowAdminLoginFromSeededAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "admin@example.com",
                        "password", "SuperSecure123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedStudent() throws Exception {
        String email = uniqueEmail("me-student");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.studentProfile").isMap())
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                // Wave-6 regression: /auth/me returns Cache-Control: no-store
                // so the JSON profile cannot be replayed from browser cache or
                // bfcache after the user logs out.
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void shouldRejectExistingTokenAfterUserIsDeactivated() throws Exception {
        String email = uniqueEmail("inactive-token");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectExistingTokenAfterExplicitLogout() throws Exception {
        String email = uniqueEmail("logout-token");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(refreshTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null && record.getUser().getId().equals(user.getId()))
                .filter(record -> record.getRevokedAt() == null)
                .count()).isZero();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedTeacher() throws Exception {
        String email = uniqueEmail("me-teacher");
        registerTeacher(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.teacherProfile").isMap())
                .andExpect(jsonPath("$.data.teacherProfile.teacherStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.studentProfile").doesNotExist());
    }

    @Test
    void shouldReturnCurrentUserForAuthenticatedAdminWithoutProfiles() throws Exception {
        String token = loginAndExtractToken("admin@example.com", "SuperSecure123!");

        MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admin@example.com"))
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
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action. Contact an administrator if needed."));
    }

    @Test
    void shouldReturnForbiddenWhenTeacherAccessesAdminRoute() throws Exception {
        String email = uniqueEmail("forbidden-teacher");
        registerTeacher(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnForbiddenWhenStudentAccessesTeacherRoute() throws Exception {
        String email = uniqueEmail("student-on-teacher");
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        mockMvc.perform(get("/api/v1/teachers/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIncludeTeacherStatusWhenTeacherLogsIn() throws Exception {
        String email = uniqueEmail("teacher-login");
        registerTeacher(email, "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.data.user.teacherStatus").value("PENDING"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformedJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
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
        registerStudent(email, "Password123!");
        String token = loginAndExtractToken(email, "Password123!");

        MvcResult result = mockMvc.perform(get("/api/v1/students/this-route-does-not-exist-" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
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
        // Tokens that were never issued must be rejected with 400. This is the public
        // contract for both "wrong token" and "already used token" because the API
        // intentionally does not differentiate the two — see AuthService.resetPassword.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "not-a-real-token-" + UUID.randomUUID(),
                        "password", "NewPassword123!",
                        "confirmPassword", "NewPassword123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("invalid")));
    }

    @Test
    void shouldRejectResetPasswordWithBlankToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", "   ",
                        "password", "NewPassword123!",
                        "confirmPassword", "NewPassword123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldCompletePasswordResetEndToEndAndAllowLoginWithNewPasswordExactlyOnce() throws Exception {
        // End-to-end: register → request reset → read token from DB → reset → login with new
        // password. We also assert single-use semantics by re-submitting the same token and
        // checking the second attempt is rejected.
        String email = uniqueEmail("reset-flow");
        String oldPassword = "Password123!";
        String newPassword = "NewPassword456!";
        registerStudent(email, oldPassword);
        String originalToken = loginAndExtractToken(email, oldPassword);
        MvcResult originalSession = login(email, oldPassword);
        String originalRefreshToken = extractRefreshCookieValue(originalSession);

        String rawResetToken = issueResetToken(email);

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null
                        && record.getUser().getId().equals(user.getId())
                        && record.getUsedAt() == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Reset token was not persisted for the registered email."));
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(token.getTokenHash()).isNotBlank().isNotEqualTo(rawResetToken);

        // First use of the token: must succeed and update the stored hash.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", rawResetToken,
                        "password", newPassword,
                        "confirmPassword", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(refreshTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null && record.getUser().getId().equals(user.getId()))
                .filter(record -> record.getRevokedAt() == null)
                .count()).isZero();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new MockCookie("mandarin_spark_refresh", originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // The old password must no longer work; the new password must.
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", oldPassword))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString());

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + originalToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Single-use enforcement: the same token cannot be redeemed a second time.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", rawResetToken,
                        "password", "AnotherNew789!",
                        "confirmPassword", "AnotherNew789!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIssueFreshTokenAndInvalidatePreviousActiveTokenWhenForgotPasswordIsCalledTwice() throws Exception {
        // If a learner clicks "forgot password" twice — for example because the first
        // email landed in spam — the older link must no longer be redeemable, otherwise
        // a stale leaked link could be used after the user explicitly asked for a new one.
        String email = uniqueEmail("reset-rotate");
        registerStudent(email, "Password123!");

        String firstRawToken = issueResetToken(email);

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        PasswordResetToken firstToken = passwordResetTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null && record.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(firstToken.getTokenHash()).isNotBlank().isNotEqualTo(firstRawToken);

        String secondRawToken = issueResetToken(email);
        assertThat(secondRawToken).isNotEqualTo(firstRawToken);

        // The original token must now carry a usedAt timestamp from the invalidation pass,
        // and a fresh still-active token must exist alongside it.
        long activeTokens = passwordResetTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null
                        && record.getUser().getId().equals(user.getId())
                        && record.getUsedAt() == null)
                .count();
        assertThat(activeTokens).isEqualTo(1L);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "token", firstRawToken,
                        "password", "NewPassword456!",
                        "confirmPassword", "NewPassword456!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldConsumePasswordResetTokenOnlyOnceAtRepositoryBoundary() throws Exception {
        String email = uniqueEmail("reset-atomic");
        registerStudent(email, "Password123!");
        issueResetToken(email);

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(record -> record.getUser() != null
                        && record.getUser().getId().equals(user.getId())
                        && record.getUsedAt() == null)
                .findFirst()
                .orElseThrow();
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
        registerStudent(registeredEmail, "Password123!");

        MvcResult existingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", registeredEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        MvcResult missingResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
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
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .with(csrf())
                    .header("X-Forwarded-For", forwardedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .header("X-Forwarded-For", forwardedIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromUntrustedRemoteAddress() throws Exception {
        String email = "forgot-untrusted-" + UUID.randomUUID() + "@example.com";
        String spoofedClientIp = "203.0.113.55";
        String actualRemoteIp = "203.0.113.200";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(actualRemoteIp);
                        return request;
                    })
                    .header("X-Forwarded-For", spoofedClientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(actualRemoteIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
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
        registerStudent(email, "Password123!");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", email,
                            "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized());
        }

        // Sixth attempt — still wrong password — should be refused at the
        // rate-limiter layer with 429 Too Many Requests, not 401.
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "WrongPassword!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));

        // And critically: even the *correct* password is refused while the
        // bucket is locked, otherwise an attacker who finally guesses right
        // would still slip through after the threshold.
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", "Password123!"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginLockoutAuditRecordShouldNotStoreEmailOrIpAddress() throws Exception {
        String email = uniqueEmail("lockout-audit");
        String forwardedIp = "198.51.100.44";
        registerStudent(email, "Password123!");
        long maxAuditIdBefore = auditLogRepository.findAll().stream()
                .mapToLong(auditLog -> auditLog.getId() == null ? 0L : auditLog.getId())
                .max()
                .orElse(0L);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .header("X-Forwarded-For", forwardedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
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
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student Test",
                        "email", email,
                        "password", password,
                        "confirmPassword", password,
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());
    }

    private void registerTeacher(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
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
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("token").asText();
    }

    private String extractRefreshCookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains("mandarin_spark_refresh=");
        String cookieValue = setCookie.substring("mandarin_spark_refresh=".length());
        int separatorIndex = cookieValue.indexOf(';');
        return separatorIndex >= 0 ? cookieValue.substring(0, separatorIndex) : cookieValue;
    }

    private String issueResetToken(String email) throws Exception {
        clearInvocations(noOpEmailService);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))))
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
}
