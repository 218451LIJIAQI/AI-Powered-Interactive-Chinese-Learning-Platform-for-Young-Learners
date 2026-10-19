package com.example.aichinese.config;

import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Requires the refresh-token cookie to carry the {@code Secure} attribute when
 * the prod profile is active (NFR-SEC-08).
 *
 * <p>Local and demo runs use plain HTTP on loopback, so the default stays
 * {@code JWT_REFRESH_COOKIE_SECURE=false} there. A production deployment is
 * expected to sit behind HTTPS; starting it with an insecure refresh cookie
 * would let the long-lived refresh token travel over clear text, so the
 * application fails closed instead.</p>
 */
@Component
public class RefreshCookieProductionGuard {

    static final String INSECURE_COOKIE_MESSAGE =
            "JWT_REFRESH_COOKIE_SECURE must be true when the prod profile is active.";

    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    public RefreshCookieProductionGuard(Environment environment, SecurityProperties securityProperties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(securityProperties, "securityProperties");
        if (environment.acceptsProfiles(PROD_PROFILES) && !securityProperties.refreshTokenCookieSecure()) {
            throw new IllegalStateException(INSECURE_COOKIE_MESSAGE);
        }
    }
}
