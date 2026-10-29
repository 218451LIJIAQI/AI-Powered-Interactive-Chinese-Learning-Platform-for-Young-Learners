package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.USER_PASSWORD;
import static com.example.aichinese.support.LearningApiTestClient.fromRemoteAddress;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * Account security (FRAUTH07/08, Proposal Table 6.2): profile update, change password with session revocation,
 * BCrypt hashing, no-store responses, expired JWT rejection, the reset-password rate limit and notification filters.
 */
class AccountSecurityIntegrationTests extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String NO_STORE = "no-store";
    private static final String BCRYPT_PATTERN = "^\\$2[aby]\\$(1[0-9]|[2-3][0-9])\\$[./A-Za-z0-9]{53}$";
    private static final String PROFILE_PATH = "/api/v1/users/me/profile";

    @Autowired
    private AuditLogRepository auditLogRepository;

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

        mockMvc.perform(api.jsonPut(PROFILE_PATH, firstSession, Map.of(
                        "displayName", "  Mei Ling  ",
                        "preferredLanguage", "zh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Mei Ling"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("zh-CN"));
        mockMvc.perform(api.jsonPut(PROFILE_PATH, firstSession, Map.of(
                        "displayName", "Mei Ling",
                        "preferredLanguage", "fr")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(changePassword(firstSession, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        mockMvc.perform(changePassword(firstSession, USER_PASSWORD, "short", "short"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(changePassword(firstSession, USER_PASSWORD, NEW_PASSWORD, "Different789!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Confirm password does not match."));

        MvcResult changed = mockMvc.perform(changePassword(firstSession, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
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
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> "PASSWORD_CHANGED".equals(log.getActionType()) && updated.getId().equals(log.getTargetId()));

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
        mockMvc.perform(changePassword(token, USER_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, NO_STORE));
    }

    @Test
    void passwordChangeLockoutIsAuditedAlthoughTheRequestIsRejected() throws Exception {
        String token = api.registerStudent(uniqueEmail("change-lockout"));
        long lastAuditIdBefore = auditLogRepository.findAll().stream().mapToLong(AuditLog::getId).max().orElse(0L);

        // FRAUTH05 / FRAUTH08: wrong current passwords are limited like sign-in; the fifth one locks the account.
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(changePassword(token, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(changePassword(token, "WrongPassword1!", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isTooManyRequests());

        // The lockout is kept although the rejected request rolls its own transaction back.
        assertThat(auditLogRepository.findAll())
                .anyMatch(log -> log.getId() > lastAuditIdBefore && "LOGIN_LOCKOUT".equals(log.getActionType()));
    }

    @Test
    void teachersAndAdminsCanAlsoManageTheirAccount() throws Exception {
        String teacherToken = api.registerTeacher(uniqueEmail("w4-account-teacher"));
        mockMvc.perform(api.jsonPut(PROFILE_PATH, teacherToken, Map.of(
                        "displayName", "Cikgu Aisyah",
                        "preferredLanguage", "ms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferredLanguage").value("ms"));
        mockMvc.perform(api.jsonPut(PROFILE_PATH, api.adminToken(), Map.of(
                        "displayName", "Platform Admin",
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());
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
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(unknownTokenReset("203.0.113.210")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(unknownTokenReset("203.0.113.210"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void notificationsCanBeFilteredByReadState() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("w4-notify-teacher"));
        JsonNode unread = api.data(mockMvc.perform(api.authGet("/api/v1/notifications?status=UNREAD", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].status").value(everyItem(is("UNREAD"))))
                .andReturn());
        assertThat(unread.path("totalItems").asLong()).isPositive();
        mockMvc.perform(api.authPatch("/api/v1/notifications/read-all", teacherToken)).andExpect(status().isOk());
        mockMvc.perform(api.authGet("/api/v1/notifications?status=UNREAD", teacherToken))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet("/api/v1/notifications?status=READ", teacherToken))
                .andExpect(jsonPath("$.data.totalItems").value(unread.path("totalItems").asLong()));
        mockMvc.perform(api.authGet("/api/v1/notifications?status=SOMETIMES", teacherToken))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder changePassword(
            String token, String currentPassword, String newPassword, String confirmPassword) throws Exception {
        return api.jsonPost("/api/v1/users/me/password", token, Map.of(
                "currentPassword", currentPassword,
                "newPassword", newPassword,
                "confirmPassword", confirmPassword));
    }

    private MockHttpServletRequestBuilder unknownTokenReset(String remoteIp) throws Exception {
        return api.jsonPost("/api/v1/auth/reset-password", null, Map.of(
                        "token", "not-a-real-token-" + UUID.randomUUID(),
                        "password", NEW_PASSWORD,
                        "confirmPassword", NEW_PASSWORD))
                .with(fromRemoteAddress(remoteIp));
    }
}
