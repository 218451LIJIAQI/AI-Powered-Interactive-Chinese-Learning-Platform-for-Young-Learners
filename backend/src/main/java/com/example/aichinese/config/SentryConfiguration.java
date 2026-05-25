package com.example.aichinese.config;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;

@Configuration
public class SentryConfiguration {

    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(bearer\\s+[a-z0-9._-]+|mandarin_spark_refresh=[^;\\s]+|password[\"':=\\s]+[^\"',\\s}]+|token[\"':=\\s]+[^\"',\\s}]+)");

    @Bean
    SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> sanitize(event);
    }

    private static SentryEvent sanitize(SentryEvent event) {
        Request request = event.getRequest();
        if (request != null) {
            sanitizeMap(request.getHeaders());
            request.setCookies(request.getCookies() == null ? null : "[Filtered]");
            Object data = request.getData();
            if (data instanceof String text) {
                request.setData(mask(text));
            }
        }
        sanitizeMap(event.getExtras());
        return event;
    }

    private static void sanitizeMap(Map<String, ?> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                setMapValue(values, entry.getKey(), "[Filtered]");
            } else if (entry.getValue() instanceof String text) {
                setMapValue(values, entry.getKey(), mask(text));
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void setMapValue(Map values, String key, String value) {
        values.put(key, value);
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.contains("authorization")
                || lower.contains("cookie")
                || lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("api-key");
    }

    private static String mask(String value) {
        return SENSITIVE_VALUE.matcher(value).replaceAll("[Filtered]");
    }
}
