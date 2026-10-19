package com.example.aichinese.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.util.TextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the answer keys that teachers store with practice activities so they stay on the server
 * (students only ever receive the questions and options).
 *
 * <p>Story question options ({@code options_payload}): a JSON array of strings, a JSON array of
 * {@code {"text": ..., "correct": true}} objects, or one option per line. The correct option is
 * {@code story_questions.correct_option_index} (0-based) or, for older rows, the option flagged
 * {@code correct} in the payload.</p>
 *
 * <p>Listening exercises ({@code question_payload}):
 * {@code {"prompt": "...", "questions": [{"question": "...", "options": ["你好", "再见"], "answer": "你好"}]}}
 * (also a single question object or a bare array of questions). Any other text is a legacy
 * free-response prompt without an answer key.</p>
 */
public final class ActivityAnswerKeys {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ActivityAnswerKeys() {
    }

    // ----- story -----

    public static List<StoryOption> parseStoryOptions(String payload) {
        String trimmed = TextUtils.trimToNull(payload);
        if (trimmed == null) {
            return List.of();
        }
        JsonNode root = readJson(trimmed);
        if (root != null && root.isObject() && root.has("options")) {
            Integer correctIndex = intValue(root, "correctIndex", "answerIndex", "correctOptionIndex");
            String answer = textValue(root, "answer", "correctAnswer");
            List<StoryOption> options = optionsFromArray(root.get("options"));
            return markCorrect(options, correctIndex, answer);
        }
        if (root != null && root.isArray()) {
            return optionsFromArray(root);
        }
        return Arrays.stream(trimmed.split("\\R"))
                .map(TextUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(text -> new StoryOption(text, false))
                .toList();
    }

    /** 0-based correct option of a story question, or null when the item has no answer key. */
    public static Integer storyCorrectIndex(StoryQuestion question, List<StoryOption> options) {
        if (question == null || options == null || options.isEmpty()) {
            return null;
        }
        Integer stored = question.getCorrectOptionIndex();
        if (stored != null) {
            return stored >= 0 && stored < options.size() ? stored : null;
        }
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).correct()) {
                return index;
            }
        }
        return null;
    }

    // ----- listening -----

    public static ListeningKey parseListening(String payload) {
        String trimmed = TextUtils.trimToNull(payload);
        if (trimmed == null) {
            return new ListeningKey(null, List.of());
        }
        JsonNode root = readJson(trimmed);
        if (root == null || !(root.isObject() || root.isArray())) {
            return new ListeningKey(trimmed, List.of());
        }
        String prompt = root.isObject() ? textValue(root, "prompt", "instructions", "instruction") : null;
        List<ListeningQuestion> questions = new ArrayList<>();
        JsonNode questionNodes = root.isArray() ? root : root.get("questions");
        if (questionNodes != null && questionNodes.isArray()) {
            questionNodes.forEach(node -> addListeningQuestion(questions, node));
        } else if (root.isObject() && (root.has("question") || root.has("options") || root.has("answer"))) {
            addListeningQuestion(questions, root);
        }
        if (questions.isEmpty() && prompt == null) {
            return new ListeningKey(trimmed, List.of());
        }
        return new ListeningKey(prompt, List.copyOf(questions));
    }

    /** Case-, width- and whitespace-insensitive comparison used for typed or chosen answers. */
    public static boolean answerMatches(String expected, String given) {
        String normalizedExpected = QuizScoring.normalizeText(expected);
        return !normalizedExpected.isEmpty() && normalizedExpected.equals(QuizScoring.normalizeText(given));
    }

    private static void addListeningQuestion(List<ListeningQuestion> target, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        String questionText = textValue(node, "question", "questionText", "text");
        List<String> options = optionsFromArray(node.get("options")).stream().map(StoryOption::text).toList();
        String answer = textValue(node, "answer", "correctAnswer");
        Integer answerIndex = intValue(node, "answerIndex", "correctIndex");
        if (answer == null && answerIndex != null && answerIndex >= 0 && answerIndex < options.size()) {
            answer = options.get(answerIndex);
        }
        target.add(new ListeningQuestion(
                questionText == null ? "Question " + (target.size() + 1) : questionText,
                options,
                answer));
    }

    private static List<StoryOption> optionsFromArray(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<StoryOption> options = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isTextual() || item.isNumber()) {
                String text = TextUtils.trimToNull(item.asText());
                if (text != null) {
                    options.add(new StoryOption(text, false));
                }
            } else if (item.isObject()) {
                String text = textValue(item, "text", "label", "option", "value");
                if (text != null) {
                    boolean correct = item.path("correct").asBoolean(false) || item.path("isCorrect").asBoolean(false);
                    options.add(new StoryOption(text, correct));
                }
            }
        }
        return List.copyOf(options);
    }

    private static List<StoryOption> markCorrect(List<StoryOption> options, Integer correctIndex, String answer) {
        List<StoryOption> marked = new ArrayList<>(options.size());
        for (int index = 0; index < options.size(); index++) {
            StoryOption option = options.get(index);
            boolean correct = option.correct()
                    || (correctIndex != null && correctIndex == index)
                    || (answer != null && answerMatches(answer, option.text()));
            marked.add(new StoryOption(option.text(), correct));
        }
        return List.copyOf(marked);
    }

    private static JsonNode readJson(String value) {
        char first = value.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }
        try {
            return MAPPER.readTree(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String textValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && (value.isTextual() || value.isNumber())) {
                String text = TextUtils.trimToNull(value.asText());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Integer intValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    /** One story option and whether it is the payload-flagged correct answer. */
    public record StoryOption(String text, boolean correct) {
    }

    /** One listening question with its (server-side) answer; a null answer accepts any non-empty reply. */
    public record ListeningQuestion(String question, List<String> options, String answer) {
    }

    /** Parsed listening payload; no questions = legacy free-response prompt. */
    public record ListeningKey(String prompt, List<ListeningQuestion> questions) {

        public boolean structured() {
            return !questions.isEmpty();
        }

        /** True when every question has an answer and each answer is one of its options (if any). */
        public boolean answerable() {
            return structured() && questions.stream().allMatch(question -> question.answer() != null
                    && (question.options().isEmpty()
                            || question.options().stream().anyMatch(option -> answerMatches(question.answer(), option))));
        }
    }
}
