package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlatformPropertiesTests {

    @Test
    void normalizesDefaultTimeZoneAndDeploymentMode() {
        PlatformProperties properties = new PlatformProperties("  Asia/Singapore  ", "  MULTI-INSTANCE  ", true, true);

        assertThat(properties.defaultTimeZone()).isEqualTo("Asia/Singapore");
        assertThat(properties.defaultZoneId().getId()).isEqualTo("Asia/Singapore");
        assertThat(properties.deploymentMode()).isEqualTo("multi-instance");
        assertThat(properties.sharedAuthRateLimitingEnabled()).isTrue();
        assertThat(properties.isMultiInstance()).isTrue();
    }

    @Test
    void rejectsInvalidTimeZoneOrDeploymentMode() {
        assertThatThrownBy(() -> new PlatformProperties("Mars/Base", "single-instance", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_DEFAULT_TIME_ZONE");

        assertThatThrownBy(() -> new PlatformProperties("Asia/Singapore", "clustered", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_DEPLOYMENT_MODE");
    }

    @Test
    void deploymentGuardRejectsProcessLocalAuthLimitingInMultiInstanceMode() {
        PlatformProperties multiInstance = new PlatformProperties("Asia/Singapore", "multi-instance", false, true);

        assertThatThrownBy(() -> new DeploymentTopologyGuard(multiInstance, new com.example.aichinese.service.LocalStorageProvider()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SHARED_AUTH_RATE_LIMITING_ENABLED");
    }

    @Test
    void deploymentGuardRejectsUnreviewedLocalStorageInMultiInstanceMode() {
        PlatformProperties multiInstance = new PlatformProperties("Asia/Singapore", "multi-instance", true, false);

        assertThatThrownBy(() -> new DeploymentTopologyGuard(multiInstance, new com.example.aichinese.service.LocalStorageProvider()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_LOCAL_STORAGE_MULTI_INSTANCE_ACKNOWLEDGED");
    }

    @Test
    void deploymentGuardAllowsSingleInstanceLocalStorage() {
        PlatformProperties singleInstance = new PlatformProperties("Asia/Singapore", "single-instance", false, false);

        assertThatCode(() -> new DeploymentTopologyGuard(singleInstance, new com.example.aichinese.service.LocalStorageProvider()))
                .doesNotThrowAnyException();
    }
}
