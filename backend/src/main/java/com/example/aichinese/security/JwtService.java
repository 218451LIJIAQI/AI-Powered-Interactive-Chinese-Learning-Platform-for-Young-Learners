package com.example.aichinese.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

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

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = Keys.hmacShaKeyFor(resolveSigningKeyBytes(securityProperties.secret()));
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + securityProperties.expiration());
        return Jwts.builder()
                .subject(user.getEmail())
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId(),
                        CLAIM_SESSION_VERSION, currentSessionVersion(user)))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String email, long sessionVersion) {
        Claims claims = extractAllClaims(token);
        return email.equalsIgnoreCase(claims.getSubject())
                && claims.getExpiration().after(new Date())
                && extractSessionVersion(claims) == sessionVersion;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private long extractSessionVersion(Claims claims) {
        Number sessionVersion = claims.get(CLAIM_SESSION_VERSION, Number.class);
        return sessionVersion == null ? 0L : sessionVersion.longValue();
    }

    private long currentSessionVersion(User user) {
        return user == null ? 0L : user.getSessionVersion();
    }

    private byte[] resolveSigningKeyBytes(String configuredSecret) {
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

    private byte[] decodeExplicitBase64Secret(String base64Secret) {
        try {
            return Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT secret uses the 'base64:' prefix but is not valid Base64.", exception);
        }
    }
}
