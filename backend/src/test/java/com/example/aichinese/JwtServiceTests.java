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
 * Unit coverage for {@code JwtService} — proves both secret formats
 * (plain-text and {@code base64:}-prefixed), the 32-byte minimum length
 * guard, and the {@code sessionVersion} claim that powers JWT
 * revocation on logout / password change.
 */
class JwtServiceTests {

    @Test
    void shouldGenerateTokenWithRuntimeStylePlainTextSecret() {
        JwtService jwtService = new JwtService(new SecurityProperties(
                "RuntimeStyleJwtSecretForTests1234567890AB",
                3_600_000L));

        String token = jwtService.generateToken(buildUser("plain-text@example.com"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("plain-text@example.com");
        assertThat(jwtService.isTokenValid(token, "plain-text@example.com", 0L)).isTrue();
    }

    @Test
    void shouldGenerateTokenWithExplicitBase64SecretPrefix() {
        String secretBytes = "0123456789abcdefghijklmnopqrstuvwxyzABCDEF";
        String base64Secret = "base64:" + Base64.getEncoder()
                .encodeToString(secretBytes.getBytes(StandardCharsets.UTF_8));
        JwtService jwtService = new JwtService(new SecurityProperties(base64Secret, 3_600_000L));

        String token = jwtService.generateToken(buildUser("base64@example.com"));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("base64@example.com");
    }

    @Test
    void shouldRejectTokenWhenSessionVersionChanges() {
        JwtService jwtService = new JwtService(new SecurityProperties(
                "RuntimeStyleJwtSecretForTests1234567890AB",
                3_600_000L));
        User user = buildUser("versioned@example.com");
        user.setSessionVersion(2L);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, "versioned@example.com", 2L)).isTrue();
        assertThat(jwtService.isTokenValid(token, "versioned@example.com", 3L)).isFalse();
    }

    @Test
    void shouldRejectShortSecretsWithClearMessage() {
        assertThatThrownBy(() -> new JwtService(new SecurityProperties("too-short-secret", 3_600_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is too short");
    }

    @Test
    void shouldReturnFalseForMalformedOrBlankTokensDuringValidation() {
        JwtService jwtService = new JwtService(new SecurityProperties(
                "RuntimeStyleJwtSecretForTests1234567890AB",
                3_600_000L));

        assertThat(jwtService.isTokenValid("not-a-jwt", "user@example.com", 0L)).isFalse();
        assertThat(jwtService.isTokenValid("   ", "user@example.com", 0L)).isFalse();
        assertThat(jwtService.isTokenValid("not-a-jwt", "   ", 0L)).isFalse();
    }

    @Test
    void shouldReturnFalseForInvalidSessionVersionClaimType() {
        String secret = "RuntimeStyleJwtSecretForTests1234567890AB";
        JwtService jwtService = new JwtService(new SecurityProperties(secret, 3_600_000L));
        String token = tokenWithClaims(secret, Map.of(
                "userId", 99L,
                "sessionVersion", "not-a-number"));

        assertThat(jwtService.isTokenValid(token, "claim-type@example.com", 0L)).isFalse();
    }

    @Test
    void shouldRejectTokenGenerationForTransientUser() {
        JwtService jwtService = new JwtService(new SecurityProperties(
                "RuntimeStyleJwtSecretForTests1234567890AB",
                3_600_000L));
        User user = buildUser("transient@example.com");
        user.setId(null);

        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no id");
    }

    private String tokenWithClaims(String secret, Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject("claim-type@example.com")
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(key)
                .compact();
    }

    private User buildUser(String email) {
        User user = new User();
        user.setId(99L);
        user.setEmail(email);
        user.setDisplayName("JWT Test User");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setActive(true);
        user.setSessionVersion(0L);
        return user;
    }
}
