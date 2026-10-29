package com.example.aichinese.support;

import org.springframework.mock.env.MockEnvironment;

import com.example.aichinese.config.SecurityProperties;

/** Configuration objects shared by the unit tests of the configuration guards and the token services. */
public final class ConfigFixtures {

    private ConfigFixtures() {
    }

    /** An environment with the given active profiles (none: the default, non-production profile). */
    public static MockEnvironment environment(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }

    /** Security settings with a 7-day refresh token in the strict {@code mandarin_spark_refresh} cookie. */
    public static SecurityProperties securityProperties(
            String secret, long accessTokenExpirationMs, boolean secureRefreshCookie) {
        return new SecurityProperties(
                secret,
                accessTokenExpirationMs,
                604_800_000L,
                "mandarin_spark_refresh",
                secureRefreshCookie,
                "Strict");
    }
}
