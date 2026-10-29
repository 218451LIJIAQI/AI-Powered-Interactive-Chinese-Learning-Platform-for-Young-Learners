package com.example.aichinese.service.ai;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.aichinese.config.HttpUrlValidation;
import com.example.aichinese.util.TextUtils;

final class AiProviderSupport {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private AiProviderSupport() {
    }

    static String validateAndNormalizeBaseUrl(String value) {
        String trimmed = TextUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        trimmed = stripTrailingSlashes(trimmed);
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
        if (isAllowedRuntimeScheme(scheme, host)) {
            return trimmed;
        }
        return null;
    }

    private static String stripTrailingSlashes(String value) {
        String stripped = value;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private static boolean isAllowedRuntimeScheme(String scheme, String host) {
        if ("https".equals(scheme)) {
            return true;
        }
        return "http".equals(scheme) && LOOPBACK_HOSTS.contains(HttpUrlValidation.normalizeHost(host));
    }

    /** The reply's {@code usage.total_tokens}, or null when it is missing, negative or beyond an {@code int}. */
    static Integer extractTokenCount(Object usageObject) {
        if (usageObject instanceof Map<?, ?> usageMap && usageMap.get("total_tokens") instanceof Number tokens) {
            double tokenCount = tokens.doubleValue();
            return tokenCount >= 0 && tokenCount <= Integer.MAX_VALUE ? tokens.intValue() : null;
        }
        return null;
    }
}
