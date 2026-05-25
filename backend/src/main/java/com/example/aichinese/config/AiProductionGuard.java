package com.example.aichinese.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
            throw new IllegalStateException(
                    "AI_ENABLE_REAL_PROVIDER must be true when the prod profile is active.");
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

    /**
     * Returns {@code true} when the {@code prod} or {@code production}
     * profile is active. Uses {@link Environment#acceptsProfiles(Profiles)}
     * so that {@code spring.profiles.default=prod} (a common Kubernetes
     * pattern where no profile is explicitly listed) also triggers the
     * guard \u2014 unlike a manual scan of {@code getActiveProfiles()}.
     */
    private boolean isProductionProfileActive() {
        return environment.acceptsProfiles(PROD_PROFILES);
    }

    private String normalizedProvider() {
        String provider = isBlank(aiProperties.getProvider())
                ? "deepseek"
                : aiProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(provider) ? "openai-compatible" : provider;
    }

    private static void requireProductionReadyProviderBaseUrl(String value) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "AI_API_BASE_URL must be configured when AI_ENABLE_REAL_PROVIDER=true and the prod profile is active.");
        }

        URI uri;
        try {
            // new URI(\u2026) is stricter than URI.create(\u2026) and rejects e.g. spaces
            // and bare hosts which the latter happily accepts as opaque URIs.
            uri = new URI(value.trim());
        } catch (URISyntaxException | IllegalArgumentException exception) {
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

    private static String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Returns {@code true} if the supplied (already-normalized) host is a
     * loopback, link-local, or unspecified address. Covers IPv4 ({@code 127/8},
     * {@code 0.0.0.0}, link-local {@code 169.254/16}), IPv6 ({@code ::1},
     * link-local {@code fe80::/10}), and the IPv4-mapped IPv6 form of loopback
     * ({@code ::ffff:127.0.0.1}). A typo of any of these in a production
     * config would otherwise short-circuit the AI provider request to an
     * unreachable internal endpoint without alarm.
     */
    private static boolean isLoopbackHost(String host) {
        return "localhost".equals(host)
                || "0.0.0.0".equals(host)
                || "::".equals(host)
                || "::1".equals(host)
                || host.startsWith("127.")
                || host.startsWith("169.254.")
                || host.startsWith("fe80:")
                || host.startsWith("::ffff:127.")
                || host.startsWith("::ffff:0.");
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
