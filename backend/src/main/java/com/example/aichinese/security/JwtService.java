package com.example.aichinese.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final String CLAIM_SESSION_VERSION = "sessionVersion";
    /** Whether the sign-in behind the token chose "Keep me signed in" (its refresh cookie outlives the browser). */
    private static final String CLAIM_PERSISTENT_SESSION = "persistent";
    /** The browser session (the sign-in's refresh-token rotation chain) the token belongs to. */
    private static final String CLAIM_SESSION_ID = "sid";

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        byte[] keyBytes = securityProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret is too short. Use a secret with at least 32 characters/bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.parser = Jwts.parser().verifyWith(signingKey).build();
    }

    /**
     * An access token of the account's current session; {@code persistentSession} is the "Keep me signed in" choice of
     * the sign-in it belongs to, so a session that replaces it (a password change) can keep that choice, and
     * {@code sessionId} names that sign-in (its refresh-token chain), so signing out of one browser ends its tokens only.
     */
    public String generateToken(User user, boolean persistentSession, String sessionId) {
        Date now = new Date();
        Date expiry = new Date(Math.addExact(now.getTime(), securityProperties.expiration()));
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_SESSION_VERSION, user.getSessionVersion())
                .claim(CLAIM_PERSISTENT_SESSION, persistentSession)
                .claim(CLAIM_SESSION_ID, sessionId)
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
        try {
            Date expiration = claims.getExpiration();
            Long tokenSessionVersion = claims.get(CLAIM_SESSION_VERSION, Long.class);
            return expiration != null
                    && expiration.after(new Date())
                    && email.equalsIgnoreCase(claims.getSubject())
                    && tokenSessionVersion != null
                    && tokenSessionVersion == sessionVersion;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /** The "Keep me signed in" choice the claims carry; a token without it (issued before the claim existed) kept it. */
    boolean isPersistentSession(Claims claims) {
        return !Boolean.FALSE.equals(claims.get(CLAIM_PERSISTENT_SESSION, Boolean.class));
    }

    /** The browser session the claims name; null for a token issued before the claim existed. */
    String sessionId(Claims claims) {
        return claims.get(CLAIM_SESSION_ID, String.class);
    }

    /** Parses and verifies the signed token; throws {@link JwtException} or {@link IllegalArgumentException} if it is invalid. */
    Claims extractAllClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
