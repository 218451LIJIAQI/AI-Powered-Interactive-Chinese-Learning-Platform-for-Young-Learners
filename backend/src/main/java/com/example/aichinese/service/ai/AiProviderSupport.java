package com.example.aichinese.service.ai;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

final class AiProviderSupport {

    /** OpenAI Responses API (GPT-5.6 Luna), the provider named in the project proposal. */
    static final String OPENAI = "openai";

    static final String DEFAULT_PROVIDER = OPENAI;

    /** Alternative Chat Completions-compatible providers kept behind the same configurable service. */
    static final String DEEPSEEK = "deepseek";

    static final String OPENAI_COMPATIBLE = "openai-compatible";

    static final Set<String> SUPPORTED_PROVIDERS = Set.of(OPENAI, DEEPSEEK, OPENAI_COMPATIBLE);

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private static final BigInteger INTEGER_MAX_VALUE = BigInteger.valueOf(Integer.MAX_VALUE);

    static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

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
        return "http".equals(scheme) && isLoopbackHost(host);
    }

    private static boolean isLoopbackHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return LOOPBACK_HOSTS.contains(normalized);
    }

    /** RestTemplate whose connect and read timeouts both use the configured AI timeout (SDD 5.6). */
    static RestTemplate restTemplate(AiProperties aiProperties, RestTemplateBuilder restTemplateBuilder) {
        Duration timeout = Duration.ofSeconds(Math.max(aiProperties.getTimeoutSeconds(), 1));
        return restTemplateBuilder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    static HttpHeaders bearerJsonHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }

    /** Configured base URL plus {@code path}, or {@code null} when the base URL is missing or not allowed. */
    static String endpoint(AiProperties aiProperties, String path) {
        String baseUrl = validateAndNormalizeBaseUrl(aiProperties.getApiBaseUrl());
        return baseUrl != null ? baseUrl + path : null;
    }

    static Integer extractTokenCount(Object usageObject) {
        if (!(usageObject instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof BigInteger bigInteger) {
            return toNonNegativeInt(bigInteger);
        }
        if (totalTokens instanceof BigDecimal bigDecimal) {
            try {
                return toNonNegativeInt(bigDecimal.toBigIntegerExact());
            } catch (ArithmeticException exception) {
                return null;
            }
        }
        if (totalTokens instanceof Number number) {
            long tokenCount = number.longValue();
            return tokenCount >= 0 && tokenCount <= Integer.MAX_VALUE ? (int) tokenCount : null;
        }
        return null;
    }

    private static Integer toNonNegativeInt(BigInteger value) {
        return value.signum() >= 0 && value.compareTo(INTEGER_MAX_VALUE) <= 0
                ? value.intValue()
                : null;
    }
}
