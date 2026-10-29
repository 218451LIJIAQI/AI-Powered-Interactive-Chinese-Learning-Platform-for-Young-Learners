package com.example.aichinese.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random one-time tokens (refresh, password-reset, e-mail change and admin invite tokens) and the SHA-256 hashes that
 * are stored instead of them (SDD Table 4.2), so a leaked database row cannot be used as a token. Each token carries
 * 256 random bits; every hash column is UNIQUE in the schema, so a (practically impossible) collision can never be
 * stored.
 */
public final class SecureTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** Base64URL without padding, for the refresh-token cookie. */
    public static String newRawToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes());
    }

    /** 64 lower-case hex characters, the letters-and-digits form a reset link and the reset form use. */
    public static String newHexToken() {
        return HexFormat.of().formatHex(randomBytes());
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for token hashing.", exception);
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
