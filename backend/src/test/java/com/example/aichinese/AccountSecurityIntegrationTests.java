package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.nextClientIp;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.support.ApiIntegrationTest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * Account security (FRAUTH07/08, Proposal Table 6.2): profile update, change password with session revocation,
 * BCrypt hashing, no-store responses, expired JWT rejection and the reset-password rate limit.
 */
class AccountSecurityIntegrationTests extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String NO_STORE = "no-store";
    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$(1[0-9]|[2-3][0-9])\\$[./A-Za-z0-9]{53}$";
    private static final String PROFILE_PATH = "/api/v1/users/me/profile";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void profileUpdateAndPasswordChangeRevokeOtherSessionsAndKeepBcryptHashes() throws Exception {
        String email = uniqueEmail("w4-account");
        String firstSession = api.registerStudent(email);
        MvcResult secondLogin = mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String secondSession = api.data(secondLogin).path("token").asText();
        Cookie secondRefreshCookie = secondLogin.getResponse().getCookie("mandarin_spark_refresh");
        assertThat(secondRefreshCookie).isNotNull();
        User user = user(email);
        // Proposal Table 6.2 "bcrypt": BCrypt format with the configured cost and a matching raw password.
        assertThat(user.getPasswordHash()).matches(BCRYPT_PATTERN).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches(USER_PASSWORD, user.getPasswordHash())).isTrue();

        jdbc.update("update student_profiles set total_points = 75 where user_id = ?", user.getId());
        mockMvc.perform(api.jsonPut(PROFILE_PATH, firstSession, Map.of(
                        "displayName", " \u00a0Mei Ling\u202f ",
                        "preferredLanguage", "zh-CN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Ling"))
                .andExpect(jsonPath("$.data.studentProfile.totalPoints").value(75))
                .andExpect(jsonPath("$.data.preferredLanguage").value("zh-CN"));
        mockMvc.perform(api.jsonPut(PROFILE_PATH, firstSession, Map.of(
                        "displayName", "\u00a0\ufeff", "preferredLanguage", "zh-CN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPut(PROFILE_PATH, firstSession, Map.of(
                        "displayName", "Mei Ling",
                        "preferredLanguage", "fr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(api.changePasswordRequest(firstSession, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mockMvc.perform(api.changePasswordRequest(firstSession, USER_PASSWORD, "short", "short"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.changePasswordRequest(firstSession, USER_PASSWORD, NEW_PASSWORD, "Different789!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm password does not match."));

        MvcResult changed = mockMvc.perform(api.changePasswordRequest(firstSession, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully. Other sessions have been signed out."))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();
        String freshToken = api.data(changed).path("token").asText();

        // Session revocation: both older tokens stop working, the fresh one works.
        mockMvc.perform(api.authGet("/api/v1/auth/me", firstSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/auth/me", secondSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/auth/me", freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Ling"));
        // SDD 3.4 / T33: the other session's refresh token is revoked as well; presenting it is refused without
        // signing out the fresh session.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(secondRefreshCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(api.authGet("/api/v1/auth/me", freshToken)).andExpect(status().isOk());

        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isUnauthorized());
        assertThat(api.login(email, NEW_PASSWORD)).isNotBlank();
        User updated = user(email);
        assertThat(updated.getPasswordHash()).matches(BCRYPT_PATTERN);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(auditEntriesAbout("User", updated.getId())).extracting(AuditLog::getActionType).contains("PASSWORD_CHANGED");

        mockMvc.perform(api.jsonPut(PROFILE_PATH, null, Map.of())).andExpect(status().isUnauthorized());
    }

    @Test
    void signInAndAccountResponsesSendNoStoreExactlyOnce() throws Exception {
        String email = uniqueEmail("no-store-account");
        String token = api.registerStudent(email);

        mockMvc.perform(api.loginRequest(email, USER_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, NO_STORE));
        mockMvc.perform(api.jsonPut(PROFILE_PATH, token, Map.of(
                        "displayName", "No Store", "preferredLanguage", "en")))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, NO_STORE));
        mockMvc.perform(api.changePasswordRequest(token, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, NO_STORE));
    }

    @Test
    void passwordChangeLockoutIsAuditedAlthoughTheRequestIsRejected() throws Exception {
        String token = api.registerStudent(uniqueEmail("change-lockout"));
        String requestId = UUID.randomUUID().toString();

        // FRAUTH05 / FRAUTH08: wrong current passwords are limited like sign-in; the fifth one locks the account.
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(api.changePasswordRequest(token, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD)
                            .header("X-Request-Id", requestId + "-" + attempt))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(api.changePasswordRequest(token, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isTooManyRequests());

        assertThat(auditForRequest(requestId + "-5", "LOGIN_LOCKOUT")).isPresent();
    }

    @Test
    void emailChangeLockoutIsAuditedLikePasswordChange() throws Exception {
        String token = api.registerStudent(uniqueEmail("email-change-lockout"));
        String requestId = UUID.randomUUID().toString();
        Map<String, String> wrongPassword = Map.of(
                "newEmail", uniqueEmail("email-change-lockout-new"), "currentPassword", "WrongPassword1!");

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(api.jsonPost("/api/v1/users/me/email-change", token, wrongPassword)
                            .header("X-Request-Id", requestId + "-" + attempt))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        }
        mockMvc.perform(api.jsonPost("/api/v1/users/me/email-change", token, wrongPassword))
                .andExpect(status().isTooManyRequests());

        assertThat(auditForRequest(requestId + "-5", "LOGIN_LOCKOUT")).isPresent();
    }

    @Test
    void adminsCanAlsoManageTheirAccount() throws Exception {
        String adminToken = api.adminToken();
        String originalName = api.data(mockMvc.perform(api.authGet("/api/v1/auth/me", adminToken))
                .andExpect(status().isOk())
                .andReturn()).path("fullName").asText();
        try {
            mockMvc.perform(api.jsonPut(PROFILE_PATH, adminToken, Map.of(
                            "displayName", "Platform Admin",
                            "preferredLanguage", "en")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("Platform Admin"));
        } finally {
            mockMvc.perform(api.jsonPut(PROFILE_PATH, adminToken, Map.of(
                            "displayName", originalName,
                            "preferredLanguage", "en")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void expiredBearerTokenIsRejectedWithUnauthorized() throws Exception {
        String email = uniqueEmail("w4-expired");
        api.registerStudent(email);
        User user = user(email);
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 120_000L);
        String expired = Jwts.builder()
                .subject(email)
                .claims(Map.of("userId", user.getId(), "sessionVersion", user.getSessionVersion()))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(past.getTime() - 60_000L))
                .expiration(past)
                .signWith(key)
                .compact();

        mockMvc.perform(api.authGet("/api/v1/auth/me", expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Your session has expired. Please sign in again to continue."));
        // Without any token nothing expired: the answer asks the visitor to sign in.
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in to continue."));
    }

    @Test
    void resetPasswordIsRateLimitedPerClientIpLikeForgotPassword() throws Exception {
        String clientIp = nextClientIp();
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(unknownTokenReset(clientIp)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(unknownTokenReset(clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    /** A reset with a token nobody was sent, from {@code clientIp}. */
    private MockHttpServletRequestBuilder unknownTokenReset(String clientIp) throws Exception {
        return api.jsonPost("/api/v1/auth/reset-password", null, Map.of(
                        "token", "not-a-real-token-" + UUID.randomUUID(),
                        "password", NEW_PASSWORD,
                        "confirmPassword", NEW_PASSWORD))
                .with(fromRemoteAddress(clientIp));
    }
}
