package com.example.aichinese.service.classes;

import java.util.ArrayList;
import java.util.List;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.entity.TracingExercise;

/**
 * The characters of a class tracing activity ("Trace 你 and 好"). They are stored on one tracing exercise: the
 * characters in {@code target_character}, and the pinyin and meaning of each character joined with {@code |} in
 * {@code pinyin} and {@code meaning}, so a practice-library exercise with a single character reads the same way.
 */
public final class TracingCharacters {

    static final String SEPARATOR = "|";

    private TracingCharacters() {
    }

    public record Entry(String character, String pinyin, String meaning) {
    }

    public static List<Entry> read(TracingExercise exercise) {
        String target = exercise.getTargetCharacter() == null ? "" : exercise.getTargetCharacter();
        List<String> characters = target.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .mapToObj(TracingCharacters::codePointText)
                .toList();
        List<String> pinyin = split(exercise.getPinyin());
        List<String> meanings = split(exercise.getMeaning());
        List<Entry> result = new ArrayList<>(characters.size());
        for (int index = 0; index < characters.size(); index++) {
            result.add(new Entry(characters.get(index), at(pinyin, index), at(meanings, index)));
        }
        return result;
    }

    public static void write(TracingExercise exercise, List<ClassRequests.TraceCharacter> characters) {
        exercise.setTargetCharacter(String.join("", characters.stream().map(ClassRequests.TraceCharacter::character).toList()));
        exercise.setPinyin(String.join(SEPARATOR, characters.stream().map(ClassRequests.TraceCharacter::pinyin).toList()));
        exercise.setMeaning(String.join(SEPARATOR, characters.stream()
                .map(character -> character.meaning() == null ? "" : character.meaning().replace(SEPARATOR, "/"))
                .toList()));
    }

    /** True when the text is exactly one character (one code point), e.g. 好 or 𠀀. */
    public static boolean isSingleCharacter(String text) {
        return text != null && text.codePointCount(0, text.length()) == 1;
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
        String value = values.get(index).strip();
        return value.isEmpty() ? null : value;
    }

    private static String codePointText(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
