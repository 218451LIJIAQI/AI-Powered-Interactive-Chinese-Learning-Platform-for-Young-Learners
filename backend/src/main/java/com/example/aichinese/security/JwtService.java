package com.example.aichinese.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates the HS256-signed JWTs used as the platform's
 * stateless session token.
 *
 * <h2>Token contents</h2>
 * <p>Each token carries three claims beyond the standard
 * {@code iat} / {@code exp} pair:</p>
 * <ul>
 *   <li>{@code sub} — the user's email address (used by Spring Security
 *       as the principal name).</li>
 *   <li>{@code userId} — the numeric user id, used by service layers to
 *       avoid an extra email-to-id lookup on every authenticated
 *       request.</li>
 *   <li>{@code sessionVersion} — snapshot of
 *       {@link User#getSessionVersion()} at issue time. The token is
 *       only accepted while this value matches the row's current
 *       version, so bumping the version invalidates every outstanding
 *       token without a server-side revocation list.</li>
 * </ul>
 *
 * <h2>Secret resolution</h2>
 * <p>The configured secret string supports two formats:</p>
 * <ol>
 *   <li><b>Plain text</b> ({@code app.security.secret=mySecret...}) —
 *       UTF-8 bytes are used directly. Must be at least 32 bytes long
 *       so HS256's 256-bit key requirement is met without
 *       silent truncation.</li>
 *   <li><b>Base64</b> ({@code app.security.secret=base64:...}) — the
 *       value after the {@code base64:} prefix is Base64-decoded, again
 *       requiring at least 32 decoded bytes. Use this when the secret
 *       is supplied as a binary key rather than a memorable phrase.</li>
 * </ol>
 *
 * <p>Both formats fail-fast at startup with a clear
 * {@link IllegalStateException} rather than at first request, so a
 * mis-configured secret never reaches production unnoticed.</p>
 *
 * <h2>Algorithm selection</h2>
 * <p>{@link Keys#hmacShaKeyFor(byte[])} chooses the HMAC variant from
 * the supplied key length: 32&ndash;47 bytes &rarr; HS256,
 * 48&ndash;63 bytes &rarr; HS384, &ge;64 bytes &rarr; HS512. To upgrade
 * an existing deployment from HS256 to HS512 simply rotate to a 64+
 * byte secret and bump {@code sessionVersion} on every user row so all
 * outstanding HS256 tokens are invalidated.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This bean is a singleton; {@link io.jsonwebtoken.JwtBuilder} and
 * {@link io.jsonwebtoken.JwtParser} from the JJWT library are
 * thread-safe so the precomputed {@link #signingKey} can be reused
 * across concurrent requests without synchronisation.</p>
 */
@Service
public class JwtService {

    private static final String BASE64_PREFIX = "base64:";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_SESSION_VERSION = "sessionVersion";
    private static final long DEFAULT_SESSION_VERSION = 0L;

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties");
        this.signingKey = Keys.hmacShaKeyFor(resolveSigningKeyBytes(securityProperties.secret()));
    }

    /**
     * Mints a fresh JWT for the supplied user. The subject is the email
     * address; the {@code userId} and {@code sessionVersion} claims power
     * the platform's stateless session revocation contract.
     *
     * @throws IllegalArgumentException if {@code user} has no email set
     *         &mdash; this would silently produce an unauthenticatable
     *         token, so we fail-fast.
     */
    public String generateToken(User user) {
        Objects.requireNonNull(user, "user");
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cannot mint a JWT for a user with no email address.");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + securityProperties.expiration());
        return Jwts.builder()
                .subject(email)
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId(),
                        CLAIM_SESSION_VERSION, currentSessionVersion(user)))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses, signature-verifies, and returns the {@code sub} claim
     * (the user email). Throws any of the
     * {@link io.jsonwebtoken.JwtException} subtypes on parse failure;
     * callers must guard for this (see
     * {@link JwtAuthenticationFilter#doFilterInternal} for the canonical
     * try/catch).
     */
    public String extractUsername(String token) {
        Objects.requireNonNull(token, "token");
        return extractAllClaims(token).getSubject();
    }

    /**
     * Returns {@code true} iff the token parses, the {@code sub} matches
     * {@code email} case-insensitively (defensive against legacy users
     * whose email was stored mixed-case), the token has not expired, and
     * the {@code sessionVersion} claim matches the caller-provided value.
     */
    public boolean isTokenValid(String token, String email, long sessionVersion) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(email, "email");
        Claims claims = extractAllClaims(token);
        Date expiration = claims.getExpiration();
        return expiration != null
                && email.equalsIgnoreCase(claims.getSubject())
                && expiration.after(new Date())
                && extractSessionVersion(claims) == sessionVersion;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static long extractSessionVersion(Claims claims) {
        Number sessionVersion = claims.get(CLAIM_SESSION_VERSION, Number.class);
        return sessionVersion == null ? DEFAULT_SESSION_VERSION : sessionVersion.longValue();
    }

    private static long currentSessionVersion(User user) {
        return user == null ? DEFAULT_SESSION_VERSION : user.getSessionVersion();
    }

    private static byte[] resolveSigningKeyBytes(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set JWT_SECRET to a strong secret with at least 32 bytes.");
        }

        byte[] keyBytes = configuredSecret.startsWith(BASE64_PREFIX)
                ? decodeExplicitBase64Secret(configuredSecret.substring(BASE64_PREFIX.length()))
                : configuredSecret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is too short. Use a plain-text secret with at least 32 characters/bytes, "
                            + "or use the prefix 'base64:' with at least 32 decoded bytes.");
        }

        return keyBytes;
    }

    private static byte[] decodeExplicitBase64Secret(String base64Secret) {
        try {
            return Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT secret uses the 'base64:' prefix but is not valid Base64.", exception);
        }
    }
}
