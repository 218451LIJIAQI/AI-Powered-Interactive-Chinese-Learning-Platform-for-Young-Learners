package com.example.aichinese.config;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class AiProductionGuard {

    private static final Set<String> SUPPORTED_REAL_PROVIDERS = Set.of("deepseek", "openai-compatible");
    private static final String PROD_CONTEXT =
            "when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active";
    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    private final Environment environment;
    private final AiProperties aiProperties;

    public AiProductionGuard(Environment environment, AiProperties aiProperties) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
        validate();
    }

    void validate() {
        if (!isProductionProfileActive()) {
            return;
        }
        if (!aiProperties.isEnableRealProvider()) {
            throw new IllegalStateException("AI_ENABLE_REAL_PROVIDER must be true when the prod profile is active.");
        }

        String provider = normalizedProvider();
        if (!SUPPORTED_REAL_PROVIDERS.contains(provider)) {
            throw new IllegalStateException(requiredMessage("AI_PROVIDER", "is not supported"));
        }
        if (isBlank(aiProperties.getApiKey())) {
            throw new IllegalStateException(requiredMessage("AI_API_KEY", "must be configured"));
        }
        if (isPlaceholder(aiProperties.getApiKey())) {
            throw new IllegalStateException(requiredMessage("AI_API_KEY", "must not use a placeholder value"));
        }
        if (isBlank(aiProperties.getModelName())) {
            throw new IllegalStateException(requiredMessage("AI_MODEL_NAME", "must be configured"));
        }
        HttpUrlValidation.requirePublicHttpsUrl(aiProperties.getApiBaseUrl(), "AI_API_BASE_URL", PROD_CONTEXT);
    }

    /**
     * Returns {@code true} when the {@code prod} or {@code production}
     * profile is active. Uses {@link Environment#acceptsProfiles(Profiles)}
     * so that {@code spring.profiles.default=prod} (a common Kubernetes
     * pattern where no profile is explicitly listed) also triggers the
     * guard, unlike a manual scan of {@code getActiveProfiles()}.
     */
    private boolean isProductionProfileActive() {
        return environment.acceptsProfiles(PROD_PROFILES);
    }

    private static String requiredMessage(String property, String requirement) {
        return property + " " + requirement + " " + PROD_CONTEXT + ".";
    }

    private String normalizedProvider() {
        String provider = isBlank(aiProperties.getProvider())
                ? "deepseek"
                : aiProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(provider) ? "openai-compatible" : provider;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("replace-with-")
                || normalized.equals("changeme")
                || normalized.equals("change-me")
                || normalized.equals("todo");
    }
}
