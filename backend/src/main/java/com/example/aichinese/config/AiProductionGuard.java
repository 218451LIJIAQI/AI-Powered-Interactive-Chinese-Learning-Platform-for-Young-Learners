package com.example.aichinese.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class AiProductionGuard {

    private static final String PROD_CONTEXT =
            "when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active";

    public AiProductionGuard(Environment environment, AiProperties aiProperties) {
        if (!ProductionProfiles.isActive(environment)) {
            return;
        }
        if (!aiProperties.isEnableRealProvider()) {
            throw new IllegalStateException("AI_ENABLE_REAL_PROVIDER must be true when the prod profile is active.");
        }

        if (!AiProperties.SUPPORTED_PROVIDERS.contains(aiProperties.normalizedProvider())) {
            throw new IllegalStateException(requiredMessage("AI_PROVIDER", "is not supported"));
        }
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new IllegalStateException(requiredMessage("AI_API_KEY", "must be configured"));
        }
        if (ProductionProfiles.isPlaceholder(aiProperties.getApiKey())) {
            throw new IllegalStateException(requiredMessage("AI_API_KEY", "must not use a placeholder value"));
        }
        if (!StringUtils.hasText(aiProperties.getModelName())) {
            throw new IllegalStateException(requiredMessage("AI_MODEL_NAME", "must be configured"));
        }
        HttpUrlValidation.requirePublicHttpsUrl(aiProperties.getApiBaseUrl(), "AI_API_BASE_URL", PROD_CONTEXT);
    }

    private static String requiredMessage(String property, String requirement) {
        return property + " " + requirement + " " + PROD_CONTEXT + ".";
    }
}
