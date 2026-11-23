package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class EnumLookups {

    private EnumLookups() {
    }

    /** Case-insensitive, ignores surrounding whitespace; null and blank find nothing. */
    public static <E extends Enum<E>> Optional<E> tryFrom(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(key))
                .findFirst();
    }
}
