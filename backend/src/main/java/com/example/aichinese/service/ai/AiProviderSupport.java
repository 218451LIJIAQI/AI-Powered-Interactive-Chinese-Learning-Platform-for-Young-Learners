package com.example.aichinese.service.ai;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import com.example.aichinese.util.TextUtils;

/**
 * Internal helpers shared across the {@code service.ai} runtime
 * components — provider-name canonicalisation, the supported-provider
 * registry, and HTTPS-only base-URL validation.
 *
 * <p>Centralising the logic here removes the four near-identical
 * inline copies that previously lived in
 * {@link AiAvailabilityService}, {@link AiProviderRouter},
 * {@link OpenAiCompatibleAiProviderClient}, and
 * {@link NoOpAiProviderClient}, and ensures the availability snapshot,
 * the runtime router, and the actual HTTP transport always agree on
 * what counts as a supported provider and a valid base URL — drift
 * between them previously meant the front-end's availability banner
 * could disagree with the actual network fallback path.</p>
 *
 * <p>The stricter loopback / link-local validation in
 * {@link com.example.aichinese.config.AiProductionGuard} stays
 * separate on purpose: the guard runs at boot to reject misconfigured
 * production deploys, whereas this helper validates per-request
 * runtime values where loopback HTTP must remain allowed for
 * integration tests and local LLM development servers.</p>
 *
 * <p>Package-private so the abstraction cannot leak outside the
 * AI service plumbing.</p>
 */
final class AiProviderSupport {

    /** Canonical default provider used when {@code app.ai.provider} is unset or blank. */
    static final String DEFAULT_PROVIDER = "deepseek";

    /** Canonical OpenAI-compatible alias produced by {@link #normalizeProvider(String)}. */
    static final String OPENAI_COMPATIBLE = "openai-compatible";

    /**
     * Providers known to the {@code service.ai} runtime. Mirrors the
     * production-guard's {@code SUPPORTED_REAL_PROVIDERS} set so an
     * environment that boots successfully under {@code prod} cannot
     * subsequently fall through to the unsupported-provider branch.
     */
    static final Set<String> SUPPORTED_PROVIDERS = Set.of(DEFAULT_PROVIDER, OPENAI_COMPATIBLE);

    /**
     * Loopback hosts on which HTTP is permitted (integration tests and
     * local LLM dev servers like Ollama on {@code localhost:11434}).
     * The {@link URI} parser already strips IPv6 brackets, so the set
     * stores the unbracketed form.
     */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private AiProviderSupport() {
        throw new AssertionError("AiProviderSupport is a static helper and must not be instantiated.");
    }

    /**
     * Returns the canonical provider name for the supplied raw value:
     * {@code null} or blank input maps to {@link #DEFAULT_PROVIDER};
     * the underscored variant {@code openai_compatible} is rewritten
     * to {@link #OPENAI_COMPATIBLE} (the public, hyphenated form);
     * otherwise the value is stripped and lower-cased.
     *
     * <p>The output is always non-{@code null} so call sites do not
     * need to defend against missing configuration.</p>
     */
    static String normalizeProvider(String raw) {
        String stripped = TextUtils.trimToNull(raw);
        if (stripped == null) {
            return DEFAULT_PROVIDER;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(lower) ? OPENAI_COMPATIBLE : lower;
    }

    /**
     * Returns {@code true} when the (already-normalised) provider name
     * is one the runtime can route. Always pair with
     * {@link #normalizeProvider(String)} on the input — passing a raw
     * configuration value will yield false negatives for the
     * {@code openai_compatible} alias.
     */
    static boolean isSupportedProvider(String normalizedProvider) {
        return SUPPORTED_PROVIDERS.contains(normalizedProvider);
    }

    /**
     * Returns {@code true} when {@code value} is a valid runtime base
     * URL — an absolute HTTPS URL, or HTTP only when the host is
     * loopback. Equivalent to
     * {@code validateAndNormalizeBaseUrl(value) != null}; provided as
     * a separate method so call sites that only need the boolean stay
     * concise.
     */
    static boolean isValidRuntimeBaseUrl(String value) {
        return validateAndNormalizeBaseUrl(value) != null;
    }

    /**
     * Parses and validates the supplied base URL, returning the
     * normalised form (stripped of surrounding whitespace and trailing
     * slashes, suitable for appending {@code /chat/completions}) or
     * {@code null} when the URL is missing, syntactically invalid,
     * uses an unsupported scheme, contains embedded credentials, or
     * targets a non-loopback host over plain HTTP.
     *
     * <p>Rejecting embedded {@code userinfo} is defence in depth — the
     * API key is supposed to travel via the {@code Authorization:
     * Bearer} header; a base URL of the form
     * {@code https://user:secret@host} would leak the credential into
     * the request line and any upstream proxy log.</p>
     */
    static String validateAndNormalizeBaseUrl(String value) {
        String trimmed = TextUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (uri.getUserInfo() != null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        if ("https".equals(scheme)) {
            return trimmed;
        }
        if ("http".equals(scheme) && isLoopbackHost(host)) {
            return trimmed;
        }
        return null;
    }

    /**
     * Returns {@code true} when the supplied host is one of the
     * loopback aliases on which HTTP traffic is permitted. The
     * {@link URI} parser strips IPv6 brackets before delivering the
     * host, but this method tolerates either bracketed or bare input
     * for callers that pass raw configuration strings.
     */
    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return LOOPBACK_HOSTS.contains(normalized);
    }
}
