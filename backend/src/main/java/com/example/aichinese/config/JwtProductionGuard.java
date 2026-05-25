package com.example.aichinese.config;

import java.util.Locale;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Rejects documented placeholder JWT signing secrets in production.
 *
 * <p>Length validation alone cannot identify a copied example value: a
 * predictable 32-character placeholder signs attacker-forgeable tokens just
 * as successfully as a random key. This guard keeps local examples usable
 * while making an unchanged production template fail closed.</p>
 */
@Component
public class JwtProductionGuard {

    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    public JwtProductionGuard(Environment environment, SecurityProperties securityProperties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(securityProperties, "securityProperties");
        if (environment.acceptsProfiles(PROD_PROFILES) && isPlaceholder(securityProperties.secret())) {
            throw new IllegalStateException(
                    "JWT_SECRET must not use a placeholder value when the prod profile is active.");
        }
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("replace-with-")
                || normalized.equals("changeme")
                || normalized.equals("change-me")
                || normalized.equals("todo");
    }
}
