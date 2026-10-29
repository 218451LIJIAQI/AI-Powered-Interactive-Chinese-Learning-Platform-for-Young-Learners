package com.example.aichinese.config;

import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** The profiles that mark a production deployment, shared by the startup guards in this package. */
final class ProductionProfiles {

    /**
     * {@code prod} or {@code production}. Checked with {@link Environment#acceptsProfiles(Profiles)}
     * so that {@code spring.profiles.default=prod} also counts, unlike a scan of the active profiles.
     */
    static final Profiles PRODUCTION = Profiles.of("prod", "production");

    private ProductionProfiles() {
    }

    static boolean isActive(Environment environment) {
        return environment.acceptsProfiles(PRODUCTION);
    }

    /** True for the documented example values that must never reach production. */
    static boolean isPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("replace-with-")
                || normalized.equals("changeme")
                || normalized.equals("change-me")
                || normalized.equals("todo");
    }
}
