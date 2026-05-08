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

@Service
public class JwtService {

    private static final String BASE64_PREFIX = "base64:";
    private static final int MINIMUM_SECRET_BYTES = 32;

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
                .claims(Map.of("userId", user.getId()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String email) {
        Claims claims = extractAllClaims(token);
        return email.equalsIgnoreCase(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
