package com.example.aichinese.service.learn;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;

/**
 * Signs the student, question set, start time and a random run ID together. Two tabs can start on the same clock tick.
 * Tokens issued before run IDs were introduced have only the signature and use the original time-based allowance.
 */
@Component
class HintRunSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_PURPOSE = "MandarinSpark hint run v1";
    private static final int MAX_TOKEN_LENGTH = 36 + 1 + 43; // UUID, dot, Base64URL SHA-256 signature

    private final SecretKeySpec key;

    HintRunSigner(SecurityProperties securityProperties) {
        this.key = new SecretKeySpec(
                mac(new SecretKeySpec(securityProperties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM),
                        KEY_PURPOSE),
                ALGORITHM);
    }

    /** The player returns this opaque token unchanged with each hint request. */
    String sign(Long studentId, String scope, LocalDateTime startedAt) {
        String id = UUID.randomUUID().toString();
        return id + "." + signature(studentId, scope, startedAt, id);
    }

    /** The authenticated run ID, or null for a verified token issued before run IDs existed. */
    String requireId(Long studentId, String scope, LocalDateTime startedAt, String token) {
        if (startedAt != null && token != null && token.length() <= MAX_TOKEN_LENGTH) {
            int separator = token.indexOf('.');
            String id = separator < 0 ? null : token.substring(0, separator);
            String supplied = token.substring(separator + 1);
            String expected = signature(studentId, scope, startedAt, id);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                return id;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Open the questions again to get a hint.");
    }

    private String signature(Long studentId, String scope, LocalDateTime startedAt, String id) {
        String signed = studentId + "\n" + scope + "\n" + startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, id == null ? signed : signed + "\n" + id));
    }

    private static byte[] mac(SecretKeySpec key, String text) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable for signing hint runs.", exception);
        }
    }
}
