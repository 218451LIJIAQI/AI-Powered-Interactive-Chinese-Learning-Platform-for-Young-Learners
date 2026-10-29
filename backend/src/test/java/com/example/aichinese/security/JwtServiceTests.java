package com.example.aichinese.security;

import static com.example.aichinese.support.ConfigFixtures.securityProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
        assertThat(jwtService.extractAllClaims(token).getSubject()).isEqualTo(email);
        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), email, 0L)).isTrue();
    }

    @Test
    void shouldGenerateTokenWithExplicitBase64SecretPrefix() {
        String email = "base64@example.com";
        String base64Secret = "base64:" + Base64.getEncoder()
                .encodeToString(BASE64_SECRET_BYTES.getBytes(StandardCharsets.UTF_8));
        JwtService jwtService = jwtService(base64Secret);

        String token = jwtService.generateToken(buildUser(email));

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractAllClaims(token).getSubject()).isEqualTo(email);
    }

    @Test
    void shouldRejectTokenWhenSessionVersionChanges() {
        String email = "versioned@example.com";
        JwtService jwtService = jwtService();
        User user = buildUser(email);
        user.setSessionVersion(2L);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), email, 2L)).isTrue();
        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), email, 3L)).isFalse();
    }

    @Test
    void shouldRejectShortSecretsWithClearMessage() {
        assertThatThrownBy(() -> jwtService(SHORT_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is too short");
    }

    @Test
    void shouldRejectMalformedOrBlankTokensDuringParsing() {
        JwtService jwtService = jwtService();

        assertThatThrownBy(() -> jwtService.extractAllClaims(MALFORMED_TOKEN)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.extractAllClaims(BLANK_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnFalseForInvalidSessionVersionClaimType() {
        JwtService jwtService = jwtService();
        String token = tokenWithClaims(Map.of("sessionVersion", INVALID_SESSION_VERSION));

        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), CLAIM_TYPE_EMAIL, 0L)).isFalse();
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

    @Test
    void shouldSetTokenExpiryFromTheConfiguredTimeToLive() {
        // Proposal Table 6.2 "JWT expiration": the token expires the configured TTL after it was issued.
        // Rejection of an expired token is pinned by shouldRejectTokenWhoseExpiryIsInThePast, so no waiting.
        long ttlMs = 90_000L;
        String token = new JwtService(securityProperties(TEST_SECRET, ttlMs, false))
                .generateToken(buildUser("short-ttl@example.com"));

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(ttlMs);
    }

    @Test
    void shouldRejectTokenWhoseExpiryIsInThePast() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 60_000L);
        String expired = Jwts.builder()
                .subject(VALIDATION_EMAIL)
                .claim("sessionVersion", 0L)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(past.getTime() - 60_000L))
                .expiration(past)
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> jwtService().extractAllClaims(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    private static JwtService jwtService() {
        return jwtService(TEST_SECRET);
    }

    private static JwtService jwtService(String secret) {
        return new JwtService(securityProperties(secret, TOKEN_TTL_MS, false));
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
        user.setSessionVersion(0L);
        return user;
    }
}
