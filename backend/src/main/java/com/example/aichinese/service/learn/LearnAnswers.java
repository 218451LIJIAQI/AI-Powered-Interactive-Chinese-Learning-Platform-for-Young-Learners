package com.example.aichinese.service.learn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Answer keys and choice lists of Learn questions, mistakes and Practice items: the JSON stored in {@code choices} /
 * {@code options} columns, and the comparison of a given answer with the key.
 */
public final class LearnAnswers {

    private static final Logger log = LoggerFactory.getLogger(LearnAnswers.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PUNCTUATION = Pattern.compile("\\p{P}+");
    /** A space with no Latin letter on either side. */
    private static final Pattern SPACE_OUTSIDE_LATIN = Pattern.compile("(?<=\\P{IsLatin}) (?=\\P{IsLatin})");

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

    public static String normalize(String value) {
        return PUNCTUATION.matcher(TextUtils.normalizeAnswerText(value)).replaceAll("");
    }

    /**
     * The first answer to each question of a finished run, in answer order. A null first answer is a recorded skip, not
     * an absent question, so a later answer does not replace it.
     */
    static <T, K> Map<K, String> firstAnswers(List<T> answers, Function<T, K> question, Function<T, String> answer) {
        Map<K, String> first = new LinkedHashMap<>();
        for (T given : answers) {
            K key = question.apply(given);
            if (!first.containsKey(key)) {
                first.put(key, answer.apply(given));
            }
        }
        return first;
    }

    /** Sentence tiles in order: the words of a "我 叫 小美" answer. */
    public static List<String> tiles(String spaceSeparated) {
        return Arrays.stream(TextUtils.stripFormSpaces(spaceSeparated).split("(?U)[\\s\\x{FEFF}]+"))
                .filter(tile -> !tile.isBlank()).toList();
    }

    /**
     * Tiles as written: a space stays only next to a Latin letter. Chinese, digits and CJK punctuation close up
     * ("教室 在 2 楼" reads "教室在2楼", "你 叫 什么 名字 ？" reads "你叫什么名字？"), while English words and pinyin
     * syllables keep their spaces ("Good night", "mā pà dú hē", "我叫 Amira"). The Learn path seed writes its sentences
     * with this rule too.
     */
    public static String joinTiles(String spaceSeparated) {
        return SPACE_OUTSIDE_LATIN.matcher(String.join(" ", tiles(spaceSeparated))).replaceAll("");
    }

    // JSON

    public static String writeOptions(List<ChoiceOption> options) {
        return optionsNode(options).toString();
    }

    static ArrayNode optionsNode(List<ChoiceOption> options) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ChoiceOption option : options) {
            ObjectNode node = array.addObject();
            node.put("text", option.text());
            if (option.pinyin() != null && !option.pinyin().isBlank()) {
                node.put("pinyin", option.pinyin());
            }
        }
        return array;
    }

    public static String writeTiles(List<String> tiles) {
        ArrayNode array = MAPPER.createArrayNode();
        tiles.forEach(array::add);
        return array.toString();
    }

    public static List<ChoiceOption> readOptions(String json) {
        return optionsOf(read(json));
    }

    static List<ChoiceOption> optionsOf(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<ChoiceOption> options = new ArrayList<>();
        for (JsonNode node : array) {
            options.add(new ChoiceOption(
                    node.path("text").asText(),
                    node.hasNonNull("pinyin") ? node.get("pinyin").asText() : null));
        }
        return List.copyOf(options);
    }

    public static List<String> readTiles(String json) {
        JsonNode array = read(json);
        if (!array.isArray()) {
            return List.of();
        }
        List<String> tiles = new ArrayList<>();
        for (JsonNode node : array) {
            tiles.add(node.asText());
        }
        return List.copyOf(tiles);
    }

    /** The index of the option that matches {@code answer}, or -1 when none does. */
    static int indexOf(List<ChoiceOption> options, String answer) {
        String key = normalize(answer);
        if (key.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < options.size(); index++) {
            if (key.equals(normalize(options.get(index).text()))) {
                return index;
            }
        }
        return -1;
    }

    /** The pinyin of the option that matches {@code answer} ({@link #indexOf}), if the options carry it. */
    static String pinyinOf(List<ChoiceOption> options, String answer) {
        int index = indexOf(options, answer);
        return index < 0 ? null : options.get(index).pinyin();
    }

    /** The parsed JSON; a blank or unreadable column reads as nothing (a node without elements). */
    static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            log.warn("Unreadable stored learn-answers JSON: {}", ExceptionLogUtils.safeSummary(exception));
            return MissingNode.getInstance();
        }
    }
}
