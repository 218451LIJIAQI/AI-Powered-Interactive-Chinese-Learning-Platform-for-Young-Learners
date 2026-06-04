package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class EnumLookups {

    private EnumLookups() {
    }

    static <E extends Enum<E>> Map<String, E> indexByName(Class<E> type) {
        Objects.requireNonNull(type, "enum type must not be null");
        return Arrays.stream(type.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(
                        value -> value.name().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Enum " + type.getName()
                                            + " declares constants whose names collide after"
                                            + " upper-casing under Locale.ROOT: '" + existing.name()
                                            + "' and '" + duplicate.name()
                                            + "'. Rename one constant so the package-wide"
                                            + " tolerant lookup remains unambiguous.");
                        }));
    }

    static <E extends Enum<E>> Optional<E> tryFrom(Map<String, E> lookup, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lookup.get(value.strip().toUpperCase(Locale.ROOT)));
    }

    static <E extends Enum<E>> E fromValue(
            Map<String, E> lookup,
            String value,
            String missingMessage,
            Supplier<String> invalidMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(missingMessage);
        }
        return tryFrom(lookup, value)
                .orElseThrow(() -> new IllegalArgumentException(invalidMessage.get()));
    }
}
