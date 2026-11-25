package com.example.aichinese.service.learn;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.aichinese.config.SecurityProperties;

/** Purpose-derived HMAC-SHA256 signer shared by stateless run tokens in {@code service.learn}. */
final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final String context;

    private HmacSigner(SecretKeySpec key, String context) {
        this.key = key;
        this.context = context;
    }

    static HmacSigner forPurpose(SecurityProperties securityProperties, String purpose, String context) {
        SecretKeySpec master = new SecretKeySpec(
                securityProperties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        return new HmacSigner(new SecretKeySpec(mac(master, purpose, context), ALGORITHM), context);
    }

    String sign(String payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, payload, context));
    }

    boolean matches(String payload, String suppliedSignature) {
        return MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                suppliedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] mac(SecretKeySpec key, String text, String context) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable for signing " + context + ".", exception);
        }
    }
}
