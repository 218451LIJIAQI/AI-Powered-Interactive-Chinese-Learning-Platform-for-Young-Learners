package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.example.aichinese.util.AiInputSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the JSON objects AI features ask the provider for. Every value is sanitised like learner input, so a reply
 * can never carry control tokens or invisible characters into the page or a stored record.
 */
public final class AiJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_OPTION_ITEMS = 8;

    private AiJson() {
    }

    /** The reply's JSON object; empty when the reply is not valid JSON (or an empty object). */
    public static Map<String, Object> parse(String text) {
        if (text == null) {
            return Map.of();
        }
        try {
            Map<String, Object> value = MAPPER.readValue(text, MAP_TYPE);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /** A text field, sanitised; null when missing or blank. */
    public static String text(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null || value instanceof Map<?, ?> || value instanceof List<?>
                ? null
                : AiInputSanitizer.sanitizeLong(String.valueOf(value));
    }

    public static boolean hasText(Map<?, ?> map, String key) {
        return text(map, key) != null;
    }

    /** A nested object, or null when the key does not hold one. */
    public static Map<?, ?> object(Map<?, ?> map, String key) {
        return map != null && map.get(key) instanceof Map<?, ?> nested ? nested : null;
    }

    /** A whole number between 0 and 100, rounded and clamped; null when the value is not a number. */
    public static Integer percent(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        double number;
        if (value instanceof Number numeric) {
            number = numeric.doubleValue();
        } else if (value instanceof String textValue) {
            try {
                number = Double.parseDouble(textValue.strip());
            } catch (NumberFormatException exception) {
                return null;
            }
        } else {
            return null;
        }
        if (!Double.isFinite(number)) {
            return null;
        }
        return (int) Math.max(0, Math.min(100, Math.round(number)));
    }

    /** The objects of a list field mapped with {@code mapper}, at most {@code maxItems}; null results are dropped. */
    public static <T> List<T> objects(Map<?, ?> map, String key, int maxItems, Function<Map<?, ?>, T> mapper) {
        if (map == null || !(map.get(key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<?, ?>) item)
                .limit(Math.min(maxItems, MAX_LIST_ITEMS))
                .map(mapper)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The non-blank short strings of a list field (answer options, tips), at most eight. */
    public static List<String> strings(Map<?, ?> map, String key) {
        if (map == null || !(map.get(key) instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .filter(item -> !(item instanceof Map<?, ?>) && !(item instanceof List<?>))
                .map(item -> AiInputSanitizer.sanitizeShort(String.valueOf(item)))
                .filter(Objects::nonNull)
                .limit(MAX_OPTION_ITEMS)
                .toList();
    }
}
