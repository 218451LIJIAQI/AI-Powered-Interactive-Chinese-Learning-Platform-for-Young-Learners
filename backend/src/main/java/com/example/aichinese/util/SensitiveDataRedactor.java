package com.example.aichinese.util;

import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central redaction rules for log, audit, and telemetry text.
 *
 * <p>Keep this class conservative: leaking one token, email address, or
 * session identifier in a log stream is much worse than over-redacting a
 * diagnostic sentence.</p>
 */
public final class SensitiveDataRedactor {

    public static final String REDACTED = "[REDACTED]";
    public static final String FILTERED = "[Filtered]";

    private static final int MAX_OBJECT_DEPTH = 8;

    private static final Pattern UNSAFE_LOG_CHARACTERS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]|[\u202A-\u202E\u2066-\u2069]");
    private static final Pattern AUTHORIZATION_HEADER_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*(?:[:=])\\s*)(?:bearer\\s+)?\\S+");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;]+");
    private static final Pattern RESET_LINK_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(reset[-_ ]?link)\\b\\s*(?:[:=])\\s*\\S+");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b((?:(?:access|refresh|id|reset|csrf|session)[-_ ]?)?(?:token|jwt)"
                    + "|password|passcode|credential|secret|api[-_ ]?key|client[-_ ]?secret"
                    + "|email)\\b\\s*(?:[:=])\\s*[^\\s\"',;&}]+");
    private static final Pattern JSON_SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)([\"'](?:password|passcode|credential|token|jwt|secret|api[-_ ]?key|client[-_ ]?secret|email)[\"']\\s*:\\s*[\"'])[^\"']+([\"'])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

    private SensitiveDataRedactor() {
        throw new AssertionError("SensitiveDataRedactor is a utility class and must not be instantiated.");
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.contains("authorization")
                || lower.contains("cookie")
                || lower.contains("credential")
                || lower.contains("csrf")
                || lower.contains("email")
                || lower.contains("jwt")
                || lower.contains("password")
                || lower.contains("passcode")
                || lower.contains("private-key")
                || lower.contains("private_key")
                || lower.contains("refresh")
                || lower.contains("session")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("api-key")
                || lower.contains("api_key")
                || lower.contains("apikey")
                || lower.contains("client-secret")
                || lower.contains("client_secret")
                || lower.contains("set-cookie");
    }

    public static String redactText(String value) {
        return redactText(value, REDACTED);
    }

    public static String redactText(String value, String replacement) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String marker = marker(replacement);
        String redacted = UNSAFE_LOG_CHARACTERS.matcher(value).replaceAll(" ");
        redacted = AUTHORIZATION_HEADER_PATTERN.matcher(redacted)
                .replaceAll(match -> match.group(1) + marker);
        redacted = BEARER_TOKEN_PATTERN.matcher(redacted)
                .replaceAll("Bearer " + marker);
        redacted = JSON_SENSITIVE_VALUE_PATTERN.matcher(redacted)
                .replaceAll(match -> match.group(1) + marker + match.group(2));
        redacted = RESET_LINK_ASSIGNMENT_PATTERN.matcher(redacted)
                .replaceAll(match -> match.group(1) + "=" + marker);
        redacted = SENSITIVE_ASSIGNMENT_PATTERN.matcher(redacted)
                .replaceAll(match -> match.group(1) + "=" + marker);
        return EMAIL_PATTERN.matcher(redacted).replaceAll(marker);
    }

    public static Object redactObject(Object value, String replacement) {
        return redactObject(value, marker(replacement), 0, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    public static <T> Map<String, T> redactMap(Map<String, T> values, String replacement) {
        if (values == null) {
            return null;
        }

        String marker = marker(replacement);
        Map<String, T> redacted = new LinkedHashMap<>(values.size());
        Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(values);
        for (Map.Entry<String, T> entry : values.entrySet()) {
            Object value = isSensitiveKey(entry.getKey())
                    ? marker
                    : redactObject(entry.getValue(), marker, 0, seen);
            redacted.put(entry.getKey(), cast(value));
        }
        return redacted;
    }

    public static Map<String, String> redactStringMap(Map<String, String> values, String replacement) {
        return redactMap(values, replacement);
    }

    private static Object redactObject(Object value, String marker, int depth, Set<Object> seen) {
        if (value instanceof String text) {
            return redactText(text, marker);
        }
        if (value == null || isScalar(value)) {
            return value;
        }
        if (depth >= MAX_OBJECT_DEPTH || seen.contains(value)) {
            return marker;
        }
        seen.add(value);

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object nested = isSensitiveKey(key)
                        ? marker
                        : redactObject(entry.getValue(), marker, depth + 1, seen);
                redacted.put(key, nested);
            }
            return redacted;
        }

        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> redacted = new java.util.ArrayList<>();
            for (Object entry : iterable) {
                redacted.add(redactObject(entry, marker, depth + 1, seen));
            }
            return redacted;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            java.util.List<Object> redacted = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                redacted.add(redactObject(Array.get(value, index), marker, depth + 1, seen));
            }
            return redacted;
        }

        return redactText(value.toString(), marker);
    }

    private static boolean isScalar(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }

    private static String marker(String replacement) {
        return replacement == null || replacement.isBlank() ? REDACTED : replacement;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
