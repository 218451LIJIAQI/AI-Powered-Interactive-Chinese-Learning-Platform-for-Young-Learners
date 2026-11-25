package com.example.aichinese.service.learn;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

    private static final String KEY_PURPOSE = "MandarinSpark hint run v1";
    private static final int MAX_TOKEN_LENGTH = 36 + 1 + 43; // UUID, dot, Base64URL SHA-256 signature

    private final HmacSigner signer;

    HintRunSigner(SecurityProperties securityProperties) {
        this.signer = HmacSigner.forPurpose(securityProperties, KEY_PURPOSE, "hint runs");
    }

    /** The player returns this opaque token unchanged with each hint request. */
    String sign(Long studentId, String scope, LocalDateTime startedAt) {
        String id = UUID.randomUUID().toString();
        return id + "." + signer.sign(payload(studentId, scope, startedAt, id));
    }

    /** The authenticated run ID, or null for a verified token issued before run IDs existed. */
    String requireId(Long studentId, String scope, LocalDateTime startedAt, String token) {
        if (startedAt != null && token != null && token.length() <= MAX_TOKEN_LENGTH) {
            int separator = token.indexOf('.');
            String id = separator < 0 ? null : token.substring(0, separator);
            String supplied = token.substring(separator + 1);
            if (signer.matches(payload(studentId, scope, startedAt, id), supplied)) {
                return id;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Open the questions again to get a hint.");
    }

    private static String payload(Long studentId, String scope, LocalDateTime startedAt, String id) {
        String signed = studentId + "\n" + scope + "\n" + startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return id == null ? signed : signed + "\n" + id;
    }
}
