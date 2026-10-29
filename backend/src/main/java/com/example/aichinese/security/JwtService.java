package com.example.aichinese.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String BASE64_PREFIX = "base64:";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String CLAIM_SESSION_VERSION = "sessionVersion";
    private static final long DEFAULT_SESSION_VERSION = 0L;

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = Keys.hmacShaKeyFor(resolveSigningKeyBytes(securityProperties.secret()));
    }

    public String generateToken(User user) {
        Objects.requireNonNull(user, "user");
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Cannot mint a JWT for a user with no email address.");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("Cannot mint a JWT for a user with no id.");
        }

        Date now = new Date();
        Date expiry = new Date(Math.addExact(now.getTime(), securityProperties.expiration()));
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_SESSION_VERSION, user.getSessionVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Checks the subject, expiry and session version of claims the caller has already verified with
     * {@link #extractAllClaims(String)}, so a request's token is parsed only once.
     */
    boolean isTokenValid(Claims claims, String email, long sessionVersion) {
        if (claims == null || email == null || email.isBlank()) {
            return false;
        }
        try {
            Date expiration = claims.getExpiration();
            String subject = claims.getSubject();
            return expiration != null
                    && subject != null
                    && email.equalsIgnoreCase(subject)
                    && expiration.after(new Date())
                    && extractSessionVersion(claims) == sessionVersion;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /** Parses and verifies the signed token; throws {@link JwtException} or {@link IllegalArgumentException} if it is invalid. */
    Claims extractAllClaims(String token) {
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

    private static byte[] resolveSigningKeyBytes(String configuredSecret) {
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
