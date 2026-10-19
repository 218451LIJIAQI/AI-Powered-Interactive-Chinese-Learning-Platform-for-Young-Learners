package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.aichinese.service.LocalStorageProvider;

class PlatformPropertiesTests {

    private static final String DEFAULT_TIME_ZONE = "Asia/Singapore";
    private static final String SINGLE_INSTANCE = "single-instance";
    private static final String MULTI_INSTANCE = "multi-instance";

    @Test
    void normalizesDefaultTimeZoneAndDeploymentMode() {
        PlatformProperties properties = platformProperties(
                "  " + DEFAULT_TIME_ZONE + "  ",
                "  MULTI-INSTANCE  ",
                true,
                true);

        assertThat(properties.defaultTimeZone()).isEqualTo(DEFAULT_TIME_ZONE);
        assertThat(properties.deploymentMode()).isEqualTo(MULTI_INSTANCE);
        assertThat(properties.sharedAuthRateLimitingEnabled()).isTrue();
        assertThat(properties.isMultiInstance()).isTrue();
    }

    @Test
    void rejectsInvalidTimeZoneOrDeploymentMode() {
        assertInvalidPlatformProperties("Mars/Base", SINGLE_INSTANCE, "APP_DEFAULT_TIME_ZONE");

        assertInvalidPlatformProperties(DEFAULT_TIME_ZONE, "clustered", "APP_DEPLOYMENT_MODE");
    }

    @Test
    void deploymentGuardRejectsProcessLocalAuthLimitingInMultiInstanceMode() {
        assertDeploymentGuardRejected(
                platformProperties(MULTI_INSTANCE, false, true),
                "APP_SHARED_AUTH_RATE_LIMITING_ENABLED");
    }

    @Test
    void deploymentGuardRejectsUnreviewedLocalStorageInMultiInstanceMode() {
        assertDeploymentGuardRejected(
                platformProperties(MULTI_INSTANCE, true, false),
                "APP_LOCAL_STORAGE_MULTI_INSTANCE_ACKNOWLEDGED");
    }

    @Test
    void deploymentGuardAllowsSingleInstanceLocalStorage() {
        assertDeploymentGuardAllowed(platformProperties(SINGLE_INSTANCE, false, false));
    }

    private static void assertInvalidPlatformProperties(
            String defaultTimeZone,
            String deploymentMode,
            String expectedMessage) {
        assertThatThrownBy(() -> platformProperties(defaultTimeZone, deploymentMode, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static void assertDeploymentGuardRejected(PlatformProperties properties, String expectedMessage) {
        assertThatThrownBy(() -> new DeploymentTopologyGuard(properties, new LocalStorageProvider()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static void assertDeploymentGuardAllowed(PlatformProperties properties) {
        assertThatCode(() -> new DeploymentTopologyGuard(properties, new LocalStorageProvider()))
                .doesNotThrowAnyException();
    }

    private static PlatformProperties platformProperties(
            String deploymentMode,
            boolean sharedAuthRateLimitingEnabled,
            boolean localStorageMultiInstanceAcknowledged) {
        return platformProperties(
                DEFAULT_TIME_ZONE,
                deploymentMode,
                sharedAuthRateLimitingEnabled,
                localStorageMultiInstanceAcknowledged);
    }

    private static PlatformProperties platformProperties(
            String defaultTimeZone,
            String deploymentMode,
            boolean sharedAuthRateLimitingEnabled,
            boolean localStorageMultiInstanceAcknowledged) {
        return new PlatformProperties(
                defaultTimeZone,
                deploymentMode,
                sharedAuthRateLimitingEnabled,
                localStorageMultiInstanceAcknowledged);
    }
}
