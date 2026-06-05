package com.example.aichinese.service.ai;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import com.example.aichinese.util.TextUtils;

final class AiProviderSupport {

    static final String DEFAULT_PROVIDER = "deepseek";

    static final String OPENAI_COMPATIBLE = "openai-compatible";

    static final Set<String> SUPPORTED_PROVIDERS = Set.of(DEFAULT_PROVIDER, OPENAI_COMPATIBLE);

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private AiProviderSupport() {
    }

    static String normalizeProvider(String raw) {
        String stripped = TextUtils.trimToNull(raw);
        if (stripped == null) {
            return DEFAULT_PROVIDER;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(lower) ? OPENAI_COMPATIBLE : lower;
    }

    static boolean isSupportedProvider(String normalizedProvider) {
        return SUPPORTED_PROVIDERS.contains(normalizedProvider);
    }

    static boolean isValidRuntimeBaseUrl(String value) {
        return validateAndNormalizeBaseUrl(value) != null;
    }

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
        if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
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

    private static boolean isLoopbackHost(String host) {
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
