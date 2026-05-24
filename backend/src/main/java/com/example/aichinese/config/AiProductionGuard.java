package com.example.aichinese.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard that refuses to launch the application when
 * the {@code prod} (or {@code production}) profile is active and the AI
 * provider is mis-configured for live use.
 *
 * <p>The platform supports two AI provider modes: a deterministic
 * stub used in development / preview and a real OpenAI-compatible /
 * DeepSeek endpoint used in production. Without this guard, a
 * mis-configured production deploy would silently fall back to the
 * stub (because the real-provider toggle defaults to {@code false}) and
 * ship learners deterministic-but-fake AI responses without any
 * operational alarm. The guard turns that silent failure into a hard
 * startup error.</p>
 *
 * <p>The validation rules are deliberately strict:</p>
 * <ul>
 *   <li>Provider must be one of the supported real providers
 *       ({@code deepseek} / {@code openai-compatible}).</li>
 *   <li>API key must be present and not a known placeholder
 *       ({@code replace-with-...}, {@code changeme}, {@code todo}).</li>
 *   <li>Model name must be present.</li>
 *   <li>Base URL must be a valid HTTPS URL targeting a non-loopback
 *       public host (so a typo of {@code localhost} cannot reach
 *       production by accident).</li>
 * </ul>
 *
 * <p>The constructor calls {@link #validate()} so the guard runs at
 * bean creation time (before any HTTP traffic), and throws
 * {@link IllegalStateException} on failure — Spring Boot stops the
 * application on a constructor failure, which is the desired
 * fail-closed behaviour.</p>
 */
@Component
public class AiProductionGuard {

    private static final Set<String> SUPPORTED_REAL_PROVIDERS = Set.of("deepseek", "openai-compatible");

    private final Environment environment;
    private final AiProperties aiProperties;

    public AiProductionGuard(Environment environment, AiProperties aiProperties) {
        this.environment = environment;
        this.aiProperties = aiProperties;
        validate();
    }

    void validate() {
        if (!isProductionProfileActive() || !aiProperties.isEnableRealProvider()) {
            return;
        }

        String provider = normalizedProvider();
        if (!SUPPORTED_REAL_PROVIDERS.contains(provider)) {
            throw new IllegalStateException(
                    "AI_PROVIDER is not supported while AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
        if (isBlank(aiProperties.getApiKey())) {
            throw new IllegalStateException(
                    "AI_API_KEY must be configured when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
        if (isPlaceholder(aiProperties.getApiKey())) {
            throw new IllegalStateException(
                    "AI_API_KEY must not use a placeholder value when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
        if (isBlank(aiProperties.getModelName())) {
            throw new IllegalStateException(
                    "AI_MODEL_NAME must be configured when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
        requireProductionReadyProviderBaseUrl(aiProperties.getApiBaseUrl());
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    private String normalizedProvider() {
        String provider = isBlank(aiProperties.getProvider())
                ? "deepseek"
                : aiProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(provider) ? "openai-compatible" : provider;
    }

    private void requireProductionReadyProviderBaseUrl(String value) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "AI_API_BASE_URL must be configured when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }

        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "AI_API_BASE_URL must be a valid HTTPS URL when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.",
                    exception);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase(Locale.ROOT);
        String host = normalizeHost(uri.getHost());
        if (!"https".equals(scheme)) {
            throw new IllegalStateException(
                    "AI_API_BASE_URL must use HTTPS when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
        if (host.isBlank() || isLoopbackHost(host)) {
            throw new IllegalStateException(
                    "AI_API_BASE_URL must target a non-loopback public host when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }
    }

    private String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isLoopbackHost(String host) {
        return "localhost".equals(host)
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || host.startsWith("127.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isPlaceholder(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("replace-with-")
                || normalized.equals("changeme")
                || normalized.equals("change-me")
                || normalized.equals("todo");
    }
}
