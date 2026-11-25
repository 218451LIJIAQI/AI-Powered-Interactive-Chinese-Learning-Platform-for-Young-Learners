package com.example.aichinese.service.classes;

import java.util.ArrayList;
import java.util.List;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.util.TextUtils;

/**
 * The characters of a class tracing activity ("Trace 你 and 好"). They are stored on one tracing exercise: the
 * characters in {@code target_character}, and the pinyin and meaning of each character joined with {@code |} in
 * {@code pinyin} and {@code meaning}, so a practice-library exercise with a single character reads the same way.
 */
public final class TracingCharacters {

    private static final String SEPARATOR = "|";

    private TracingCharacters() {
    }

    public static List<LessonViews.TraceCharacter> read(TracingExercise exercise) {
        String target = exercise.getTargetCharacter() == null ? "" : exercise.getTargetCharacter();
        List<String> characters = target.codePoints()
                .filter(codePoint -> !TextUtils.isSpace(codePoint))
                .mapToObj(Character::toString)
                .toList();
        List<String> pinyin = split(exercise.getPinyin());
        List<String> meanings = split(exercise.getMeaning());
        List<LessonViews.TraceCharacter> result = new ArrayList<>(characters.size());
        for (int index = 0; index < characters.size(); index++) {
            result.add(new LessonViews.TraceCharacter(characters.get(index), at(pinyin, index), at(meanings, index)));
        }
        return result;
    }

    public static void write(TracingExercise exercise, List<ClassRequests.TraceCharacter> characters) {
        exercise.setTargetCharacter(String.join("", characters.stream().map(ClassRequests.TraceCharacter::character).toList()));
        exercise.setPinyin(joined(characters.stream().map(ClassRequests.TraceCharacter::pinyin).toList()));
        exercise.setMeaning(joined(characters.stream().map(ClassRequests.TraceCharacter::meaning).toList()));
    }

    /** The characters as {@link #read} returns them after {@link #write}, without allocating an entity. */
    static List<LessonViews.TraceCharacter> normalize(List<ClassRequests.TraceCharacter> characters) {
        return characters.stream()
                .map(character -> new LessonViews.TraceCharacter(
                        character.character(),
                        cleanField(character.pinyin()),
                        cleanField(character.meaning())))
                .toList();
    }

    private static String cleanField(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = TextUtils.stripFormSpaces(value.replace(SEPARATOR, "/"));
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** One value per character; a separator typed inside a value would shift the values after it. */
    private static String joined(List<String> values) {
        return String.join(SEPARATOR, values.stream()
                .map(value -> value == null ? "" : value.replace(SEPARATOR, "/"))
                .toList());
    }

    /** True when the text is exactly one character (one code point), e.g. 好 or 𠀀. */
    public static boolean isSingleCharacter(String text) {
        return !TextUtils.isBlankOrSpaces(text) && text.codePointCount(0, text.length()) == 1;
    }

    private static List<String> split(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split("\\" + SEPARATOR, -1));
    }

    private static String at(List<String> values, int index) {
        if (index >= values.size()) {
            return null;
        }
        String value = TextUtils.stripFormSpaces(values.get(index));
        return value.isEmpty() ? null : value;
    }
}
