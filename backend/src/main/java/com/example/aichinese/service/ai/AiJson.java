package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import com.example.aichinese.util.AiInputSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the JSON objects AI features ask the provider for. Every value is sanitised like learner input, so a reply
 * can never carry control tokens or invisible characters into the page or a stored record.
 */
public final class AiJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_OPTION_ITEMS = 8;

    private AiJson() {
    }

    /** The reply's JSON object; empty when the reply is not valid JSON (or an empty object). */
    public static Map<String, Object> parse(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = MAPPER.readValue(text, MAP_TYPE);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * Tests every sanitised string leaf value of a parsed reply individually (short-circuiting on the first match).
     * Screening leaves separately avoids false-positive cross-leaf boundary matches while still decoding JSON escapes.
     */
    public static boolean anyTextMatches(Map<?, ?> map, Predicate<String> predicate) {
        return map != null && !map.isEmpty() && valueMatches(map, predicate);
    }

    private static boolean valueMatches(Object value, Predicate<String> predicate) {
        if (value instanceof Map<?, ?> nested) {
            for (Object item : nested.values()) {
                if (valueMatches(item, predicate)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (valueMatches(item, predicate)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof String string) {
            String sanitized = AiInputSanitizer.sanitizeUnbounded(string);
            return sanitized != null && predicate.test(sanitized);
        }
        return false;
    }

    /** A sanitised JSON string. Missing, blank and non-string fields have no text. */
    public static String text(Map<?, ?> map, String key) {
        return sanitizedString(get(map, key), AiInputSanitizer.DEFAULT_LONG_INPUT_LIMIT);
    }

    private static Object get(Map<?, ?> map, String key) {
        return map == null ? null : map.get(key);
    }

    private static String sanitizedString(Object value, int maxLength) {
        return value instanceof String string ? AiInputSanitizer.sanitize(string, maxLength) : null;
    }

    public static boolean hasText(Map<?, ?> map, String key) {
        return text(map, key) != null;
    }

    /** A nested object, or null when the key does not hold one. */
    public static Map<?, ?> object(Map<?, ?> map, String key) {
        return get(map, key) instanceof Map<?, ?> nested ? nested : null;
    }

    /** A numeric percentage in range, rounded to the nearest whole number. */
    public static Integer percent(Map<?, ?> map, String key) {
        if (!(get(map, key) instanceof Number number)) {
            return null;
        }
        double percent = number.doubleValue();
        return percent >= 0 && percent <= 100 ? (int) Math.round(percent) : null;
    }

    /** The objects of a list field mapped with {@code mapper}, at most {@code maxItems}; null results are dropped. */
    public static <T> List<T> objects(Map<?, ?> map, String key, int maxItems, Function<Map<?, ?>, T> mapper) {
        if (maxItems <= 0 || !(get(map, key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<?, ?>) item)
                .map(mapper)
                .filter(Objects::nonNull)
                .limit(maxItems)
                .toList();
    }

    /** The non-blank short strings of a list field (answer options, tips), at most eight. */
    public static List<String> strings(Map<?, ?> map, String key) {
        if (!(get(map, key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> sanitizedString(item, AiInputSanitizer.DEFAULT_SHORT_INPUT_LIMIT))
                .filter(Objects::nonNull)
                .limit(MAX_OPTION_ITEMS)
                .toList();
    }
}
