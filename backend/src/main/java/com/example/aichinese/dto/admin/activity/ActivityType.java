package com.example.aichinese.dto.admin.activity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ActivityType {

    FLASHCARD,

    MATCHING_GAME,

    LISTENING_EXERCISE,

    TRACING_EXERCISE,

    DRAG_DROP_ACTIVITY,

    STORY_ACTIVITY;

    private static final Map<String, ActivityType> LOOKUP =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            Enum::name,
                            Function.identity()));

    private static final String ALLOWED_VALUES_LIST =
            Arrays.stream(values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

    @JsonCreator
    public static ActivityType fromJson(String value) {
        if (value == null) {
            return null;
        }
        String normalised = normalise(value);
        if (normalised == null) {
            return null;
        }
        ActivityType match = LOOKUP.get(normalised);
        if (match == null) {
            String echo = value.length() > 64 ? value.substring(0, 64) + "..." : value;
            throw new IllegalArgumentException(
                    "Unknown activity type: '" + echo
                            + "'. Allowed values are " + ALLOWED_VALUES_LIST + ".");
        }
        return match;
    }

    public static Optional<ActivityType> parseLenient(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = normalise(value);
        if (normalised == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(normalised));
    }

    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalise(String value) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
