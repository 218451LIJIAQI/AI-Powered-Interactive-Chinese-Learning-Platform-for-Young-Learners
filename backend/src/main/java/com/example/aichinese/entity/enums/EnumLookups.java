package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

final class EnumLookups {

    private EnumLookups() {
    }

    static <E extends Enum<E>> Optional<E> tryFrom(Class<E> type, String value) {
        return normalizeKey(value).flatMap(key -> Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().toUpperCase(Locale.ROOT).equals(key))
                .findFirst());
    }

    static <E extends Enum<E>> E fromValue(
            Class<E> type,
            String value,
            String missingMessage,
            Supplier<String> invalidMessage) {
        if (normalizeKey(value).isEmpty()) {
            throw new IllegalArgumentException(missingMessage);
        }
        return tryFrom(type, value).orElseThrow(() -> new IllegalArgumentException(invalidMessage.get()));
    }

    private static Optional<String> normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.strip().toUpperCase(Locale.ROOT));
    }
}
