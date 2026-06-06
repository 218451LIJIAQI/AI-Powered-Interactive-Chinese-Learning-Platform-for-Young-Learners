package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.User;
import com.example.aichinese.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Unit coverage for {@code JwtService} - proves both secret formats
 * (plain-text and {@code base64:}-prefixed), the 32-byte minimum length
 * guard, and the {@code sessionVersion} claim that powers JWT
 * revocation on logout / password change.
 */
class JwtServiceTests {

    private static final String TEST_SECRET = "RuntimeStyleJwtSecretForTests1234567890AB";
    private static final String BASE64_SECRET_BYTES = "0123456789abcdefghijklmnopqrstuvwxyzABCDEF";
    private static final String CLAIM_TYPE_EMAIL = "claim-type@example.com";
    private static final String VALIDATION_EMAIL = "user@example.com";
    private static final String MALFORMED_TOKEN = "not-a-jwt";
    private static final String BLANK_VALUE = "   ";
    private static final String SHORT_SECRET = "too-short-secret";
    private static final String INVALID_SESSION_VERSION = "not-a-number";
    private static final long TOKEN_TTL_MS = 3_600_000L;
    private static final long TEST_USER_ID = 99L;

    @Test
    void shouldGenerateTokenWithRuntimeStylePlainTextSecret() {
        String email = "plain-text@example.com";
        JwtService jwtService = jwtService();

        String token = jwtService.generateToken(buildUser(email));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo(email);
        assertThat(jwtService.isTokenValid(token, email, 0L)).isTrue();
    }

    @Test
    void shouldGenerateTokenWithExplicitBase64SecretPrefix() {
        String email = "base64@example.com";
        String base64Secret = "base64:" + Base64.getEncoder()
                .encodeToString(BASE64_SECRET_BYTES.getBytes(StandardCharsets.UTF_8));
        JwtService jwtService = jwtService(base64Secret);

        String token = jwtService.generateToken(buildUser(email));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo(email);
    }

    @Test
    void shouldRejectTokenWhenSessionVersionChanges() {
        String email = "versioned@example.com";
        JwtService jwtService = jwtService();
        User user = buildUser(email);
        user.setSessionVersion(2L);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, email, 2L)).isTrue();
        assertThat(jwtService.isTokenValid(token, email, 3L)).isFalse();
    }

    @Test
    void shouldRejectShortSecretsWithClearMessage() {
        assertThatThrownBy(() -> jwtService(SHORT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is too short");
    }

    @Test
    void shouldReturnFalseForMalformedOrBlankTokensDuringValidation() {
        JwtService jwtService = jwtService();

        assertThat(jwtService.isTokenValid(MALFORMED_TOKEN, VALIDATION_EMAIL, 0L)).isFalse();
        assertThat(jwtService.isTokenValid(BLANK_VALUE, VALIDATION_EMAIL, 0L)).isFalse();
        assertThat(jwtService.isTokenValid(MALFORMED_TOKEN, BLANK_VALUE, 0L)).isFalse();
    }

    @Test
    void shouldReturnFalseForInvalidSessionVersionClaimType() {
        JwtService jwtService = jwtService();
        String token = tokenWithClaims(Map.of(
                "userId", TEST_USER_ID,
                "sessionVersion", INVALID_SESSION_VERSION));

        assertThat(jwtService.isTokenValid(token, CLAIM_TYPE_EMAIL, 0L)).isFalse();
    }

    @Test
    void shouldRejectTokenGenerationForTransientUser() {
        JwtService jwtService = jwtService();
        User user = buildUser("transient@example.com");
        user.setId(null);

        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no id");
    }

    private static JwtService jwtService() {
        return jwtService(TEST_SECRET);
    }

    private static JwtService jwtService(String secret) {
        return new JwtService(new SecurityProperties(secret, TOKEN_TTL_MS));
    }

    private String tokenWithClaims(Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(CLAIM_TYPE_EMAIL)
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL_MS))
                .signWith(key)
                .compact();
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setEmail(email);
        user.setDisplayName("JWT Test User");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setActive(true);
        user.setSessionVersion(0L);
        return user;
    }
}
