package com.example.aichinese.config;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.service.LocalStorageProvider;
import com.example.aichinese.service.storage.StorageProvider;

/**
 * Refuses startup for deployment topologies the current persistence layer
 * cannot safely support.
 */
@Component
public class DeploymentTopologyGuard {

    private final PlatformProperties platformProperties;
    private final StorageProvider storageProvider;

    public DeploymentTopologyGuard(PlatformProperties platformProperties, StorageProvider storageProvider) {
        this.platformProperties = Objects.requireNonNull(platformProperties, "platformProperties");
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
        validate();
    }

    void validate() {
        if (!platformProperties.isMultiInstance()) {
            return;
        }

        if (!platformProperties.sharedAuthRateLimitingEnabled()) {
            throw new IllegalStateException(
                    "APP_DEPLOYMENT_MODE=multi-instance requires APP_SHARED_AUTH_RATE_LIMITING_ENABLED=true "
                            + "and a shared/database-backed authentication rate limiter. The process-local "
                            + "login and public-auth limiters are only safe for single-instance deployments.");
        }

        if (usesUnacknowledgedLocalStorage()) {
            throw new IllegalStateException(
                    "APP_DEPLOYMENT_MODE=multi-instance requires shared object storage or "
                            + "APP_LOCAL_STORAGE_MULTI_INSTANCE_ACKNOWLEDGED=true after a controlled shared-volume review.");
        }
    }

    private boolean usesUnacknowledgedLocalStorage() {
        return storageProvider instanceof LocalStorageProvider
                && !platformProperties.localStorageMultiInstanceAcknowledged();
    }
}
