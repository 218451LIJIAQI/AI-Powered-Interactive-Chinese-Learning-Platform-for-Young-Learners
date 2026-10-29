package com.example.aichinese.service.learn;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.example.aichinese.dto.learn.ChoiceOption;

/**
 * Answer keys and choice lists of Learn questions, mistakes and Practice items: the JSON stored in {@code choices} /
 * {@code options} columns, and the comparison of a given answer with the key.
 */
public final class LearnAnswers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LearnAnswers() {
    }

    /**
     * True when {@code given} says the same as {@code expected}, ignoring case, width, spaces and punctuation (a built
     * sentence "我 叫 小美" matches "我叫小美。"). Blank answers never match.
     */
    public static boolean matches(String expected, String given) {
        String normalizedExpected = normalize(expected);
        return !normalizedExpected.isEmpty() && normalizedExpected.equals(normalize(given));
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String folded = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(folded.length());
        folded.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint) && !isPunctuation(codePoint))
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private static boolean isPunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    /** Sentence tiles in order: the words of a "我 叫 小美" answer. */
    public static List<String> tiles(String spaceSeparated) {
        if (spaceSeparated == null || spaceSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(spaceSeparated.strip().split("\\s+")).filter(tile -> !tile.isBlank()).toList();
    }

    /** "我 叫 小美" as the student reads it: "我叫小美". */
    public static String joinTiles(String spaceSeparated) {
        return String.join("", tiles(spaceSeparated));
    }

    // ===== JSON =====

    public static String writeOptions(List<ChoiceOption> options) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ChoiceOption option : options) {
            ObjectNode node = array.addObject();
            node.put("text", option.text());
            if (option.pinyin() != null && !option.pinyin().isBlank()) {
                node.put("pinyin", option.pinyin());
            }
        }
        return array.toString();
    }

    public static String writeTiles(List<String> tiles) {
        ArrayNode array = MAPPER.createArrayNode();
        tiles.forEach(array::add);
        return array.toString();
    }

    /** Options of a choice question; plain strings are read as options without pinyin. */
    public static List<ChoiceOption> readOptions(String json) {
        JsonNode root = read(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<ChoiceOption> options = new ArrayList<>();
        for (JsonNode node : root) {
            if (node.isTextual()) {
                options.add(new ChoiceOption(node.asText(), null));
            } else if (node.isObject() && node.hasNonNull("text")) {
                options.add(new ChoiceOption(
                        node.get("text").asText(),
                        node.hasNonNull("pinyin") ? node.get("pinyin").asText() : null));
            }
        }
        return List.copyOf(options);
    }

    public static List<String> readTiles(String json) {
        JsonNode root = read(json);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<String> tiles = new ArrayList<>();
        for (JsonNode node : root) {
            if (node.isTextual() && !node.asText().isBlank()) {
                tiles.add(node.asText());
            }
        }
        return List.copyOf(tiles);
    }

    /** The pinyin of the option whose text is {@code text}, if the options carry it. */
    public static String pinyinOf(List<ChoiceOption> options, String text) {
        return options.stream()
                .filter(option -> Objects.equals(option.text(), text))
                .map(ChoiceOption::pinyin)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
