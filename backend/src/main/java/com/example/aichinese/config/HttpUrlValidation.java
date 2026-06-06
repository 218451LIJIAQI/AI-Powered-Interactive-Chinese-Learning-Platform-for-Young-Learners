package com.example.aichinese.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Package-local URL validation helpers shared by startup guards and
 * configuration-property records.
 */
final class HttpUrlValidation {

    private HttpUrlValidation() {
    }

    static URI requireAbsoluteHttpUrl(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured.");
        }
        URI uri = parseStrict(value, label);
        String scheme = normalizeScheme(uri.getScheme());
        if (!("http".equals(scheme) || "https".equals(scheme)) || normalizeHost(uri.getHost()).isBlank()) {
            throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URL.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(label + " must not contain embedded credentials.");
        }
        return uri;
    }

    static URI requirePublicHttpsUrl(String value, String label, String productionContext) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must be configured " + productionContext + ".");
        }
        URI uri;
        try {
            uri = requireAbsoluteHttpUrl(value, label);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " must be a valid HTTPS URL " + productionContext + ".", exception);
        }
        String scheme = normalizeScheme(uri.getScheme());
        String host = normalizeHost(uri.getHost());
        if (!"https".equals(scheme)) {
            throw new IllegalStateException(label + " must use HTTPS " + productionContext + ".");
        }
        if (host.isBlank() || isLoopbackOrLinkLocalHost(host)) {
            throw new IllegalStateException(label + " must target a non-loopback public host " + productionContext + ".");
        }
        return uri;
    }

    static URI parseStrict(String value, String label) {
        try {
            return new URI(value == null ? "" : value.strip());
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " must be a valid URL.", exception);
        }
    }

    static String normalizeScheme(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    static String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    static boolean isLoopbackOrLinkLocalHost(String host) {
        String normalized = normalizeHost(host);
        return "localhost".equals(normalized)
                || "0.0.0.0".equals(normalized)
                || "::".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("127.")
                || normalized.startsWith("169.254.")
                || normalized.startsWith("fe80:")
                || normalized.startsWith("::ffff:127.")
                || normalized.startsWith("::ffff:0.");
    }
}
