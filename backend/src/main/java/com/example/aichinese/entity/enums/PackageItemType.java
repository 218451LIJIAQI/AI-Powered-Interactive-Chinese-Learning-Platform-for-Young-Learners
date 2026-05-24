package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kind of artefact bundled inside a {@code CoursePackage}.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code PackageItem}. The constant names are part of the database
 * contract — they MUST NOT be renamed or removed without a paired
 * Flyway migration.</p>
 *
 * <p>This is a strict subset of {@link ManagedContentType}: a package
 * may bundle modules or lessons, but never another package (preventing
 * cyclic / nested-package edge cases).</p>
 */
public enum PackageItemType {

    /** A {@code LearningModule} included as a unit inside the package. */
    MODULE,

    /** A {@code Lesson} included as a unit inside the package. */
    LESSON;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PackageItemType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised package item type
     */
    public static Optional<PackageItemType> tryFrom(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(trimmed.toUpperCase(Locale.ROOT)));
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * <p>Behaviour preserved from the original implementation: {@code null}
     * or blank input throws "{@code Package item type is required.}",
     * invalid input throws
     * "{@code Invalid package item type. Allowed values are MODULE or LESSON.}".</p>
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised package item type
     */
    public static PackageItemType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Package item type is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid package item type. Allowed values are MODULE or LESSON."));
    }

    /** @return {@code true} when this item denotes a {@link #MODULE}. */
    public boolean isModule() {
        return this == MODULE;
    }

    /** @return {@code true} when this item denotes a {@link #LESSON}. */
    public boolean isLesson() {
        return this == LESSON;
    }
}
