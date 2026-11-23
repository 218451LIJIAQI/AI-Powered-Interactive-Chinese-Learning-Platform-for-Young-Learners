package com.example.aichinese.security;

import static com.example.aichinese.support.ConfigFixtures.securityProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.security.Keys;

/**
 * Unit coverage for {@code JwtService} - proves the 32-byte minimum length
 * guard and the {@code sessionVersion} claim that powers JWT
 * revocation on logout / password change.
 */
class JwtServiceTests {

    private static final String TEST_SECRET = "RuntimeStyleJwtSecretForTests1234567890AB";
    private static final String VALIDATION_EMAIL = "user@example.com";
    private static final String MALFORMED_TOKEN = "not-a-jwt";
    private static final String BLANK_VALUE = "   ";
    private static final String SHORT_SECRET = "too-short-secret";
    private static final long TOKEN_TTL_MS = 3_600_000L;

    @Test
    void shouldGenerateTokenWithRuntimeStylePlainTextSecret() {
        String email = "plain-text@example.com";
        JwtService jwtService = jwtService();

        String token = jwtService.generateToken(buildUser(email), true, "session-1");

        assertThat(jwtService.extractAllClaims(token).getSubject()).isEqualTo(email);
        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), email, 0L)).isTrue();
    }

    /** The token names its browser session (sign-out of one device); a token from before sessions were named has none. */
    @Test
    void shouldNameTheBrowserSessionOfTheSignIn() {
        JwtService jwtService = jwtService();

        assertThat(jwtService.sessionId(jwtService.extractAllClaims(
                jwtService.generateToken(buildUser(VALIDATION_EMAIL), true, "family-42")))).isEqualTo("family-42");
        assertThat(jwtService.sessionId(jwtService.extractAllClaims(
                signedToken(0L, new Date(System.currentTimeMillis() + TOKEN_TTL_MS))))).isNull();
    }

    /** A session that replaces the token's (a password change) keeps the sign-in's "Keep me signed in" choice. */
    @Test
    void shouldCarryTheKeepMeSignedInChoiceOfTheSignIn() {
        JwtService jwtService = jwtService();

        assertThat(jwtService.isPersistentSession(
                jwtService.extractAllClaims(jwtService.generateToken(buildUser(VALIDATION_EMAIL), false, "session-1")))).isFalse();
        assertThat(jwtService.isPersistentSession(
                jwtService.extractAllClaims(jwtService.generateToken(buildUser(VALIDATION_EMAIL), true, "session-1")))).isTrue();
        // A token issued before the claim existed counts as kept, as such a session was.
        assertThat(jwtService.isPersistentSession(jwtService.extractAllClaims(
                signedToken(0L, new Date(System.currentTimeMillis() + TOKEN_TTL_MS))))).isTrue();
    }

    @Test
    void shouldRejectTokenWhenSessionVersionChanges() {
        String email = "versioned@example.com";
        JwtService jwtService = jwtService();
        User user = buildUser(email);
        user.setSessionVersion(2L);

        String token = jwtService.generateToken(user, true, "session-1");

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
        // A fractional version must not be rounded down to the account's current version.
        for (Object version : new Object[]{"not-a-number", 0.5}) {
            String token = signedToken(version, new Date(System.currentTimeMillis() + TOKEN_TTL_MS));
            assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), VALIDATION_EMAIL, 0L)).isFalse();
        }
    }

    @Test
    void browserSessionClaimsRequireTheirDeclaredTypes() {
        JwtService jwtService = jwtService();
        Claims claims = Jwts.claims().add("persistent", "false").add("sid", 42).build();

        assertThatThrownBy(() -> jwtService.isPersistentSession(claims)).isInstanceOf(RequiredTypeException.class);
        assertThatThrownBy(() -> jwtService.sessionId(claims)).isInstanceOf(RequiredTypeException.class);
    }

    @Test
    void shouldReturnFalseWhenTheSessionVersionClaimIsMissing() {
        JwtService jwtService = jwtService();
        String token = signedToken(null, new Date(System.currentTimeMillis() + TOKEN_TTL_MS));

        assertThat(jwtService.isTokenValid(jwtService.extractAllClaims(token), VALIDATION_EMAIL, 0L)).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtService otherKey = jwtService("AnotherRuntimeStyleJwtSecretForTests12345");
        String token = otherKey.generateToken(buildUser(VALIDATION_EMAIL), true, "session-1");

        assertThatThrownBy(() -> jwtService().extractAllClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldSetTokenExpiryFromTheConfiguredTimeToLive() {
        // Proposal Table 6.2 "JWT expiration": the token expires the configured TTL after it was issued.
        // Rejection of an expired token is pinned by shouldRejectTokenWhoseExpiryIsInThePast, so no waiting.
        long ttlMs = 90_000L;
        JwtService jwtService = new JwtService(securityProperties(TEST_SECRET, ttlMs, false));

        Claims claims = jwtService.extractAllClaims(jwtService.generateToken(buildUser("short-ttl@example.com"), true, "session-1"));

        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(ttlMs);
    }

    @Test
    void shouldRejectTokenWhoseExpiryIsInThePast() {
        String expired = signedToken(0L, new Date(System.currentTimeMillis() - 60_000L));

        assertThatThrownBy(() -> jwtService().extractAllClaims(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldValidateSubjectCaseInsensitivelyAndRejectDifferentEmail() {
        JwtService jwtService = jwtService();
        String token = jwtService.generateToken(buildUser(VALIDATION_EMAIL), true, "session-1");
        Claims claims = jwtService.extractAllClaims(token);

        assertThat(jwtService.isTokenValid(claims, VALIDATION_EMAIL.toUpperCase(), 0L)).isTrue();
        assertThat(jwtService.isTokenValid(claims, "other@example.com", 0L)).isFalse();
    }

    @Test
    void shouldRejectSignedTokenWithoutExpirationClaimAndExpiredClaimsDirectly() {
        JwtService jwtService = jwtService();
        String withoutExp = signedToken(0L, null);
        Claims parsedWithoutExp = jwtService.extractAllClaims(withoutExp);

        assertThat(parsedWithoutExp.getExpiration()).isNull();
        assertThat(jwtService.isTokenValid(parsedWithoutExp, VALIDATION_EMAIL, 0L)).isFalse();

        Claims expiredClaims = Jwts.claims()
                .subject(VALIDATION_EMAIL)
                .add("sessionVersion", 0L)
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .build();
        assertThat(jwtService.isTokenValid(expiredClaims, VALIDATION_EMAIL, 0L)).isFalse();
    }

    private static JwtService jwtService() {
        return jwtService(TEST_SECRET);
    }

    private static JwtService jwtService(String secret) {
        return new JwtService(securityProperties(secret, TOKEN_TTL_MS, false));
    }

    /** A token validly signed with the test secret, with values {@link JwtService} itself would never issue. */
    private static String signedToken(Object sessionVersion, Date expiry) {
        return Jwts.builder()
                .subject(VALIDATION_EMAIL)
                .claim("sessionVersion", sessionVersion)
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private User buildUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setSessionVersion(0L);
        return user;
    }
}
