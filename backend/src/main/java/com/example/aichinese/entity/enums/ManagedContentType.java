package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Top-level kind of content the admin moderation tooling can act upon.
 *
 * <p>Used by {@code AdminContentReviewService} and {@code AdminController}
 * to discriminate the entity behind composite identifiers like
 * {@code "MODULE:42"}, {@code "LESSON:7"}, {@code "PACKAGE:11"}; also
 * used by {@code PublicContentService} when materialising mixed-type
 * catalogue rows.</p>
 *
 * <p>Note this enum is <em>not</em> persisted directly; it is parsed from
 * controller path variables and admin DTOs. Even so, the constant names
 * appear in admin-tool URLs and JSON payloads, so they MUST NOT be
 * renamed without a coordinated frontend change.</p>
 */
public enum ManagedContentType {

    /** A teacher-authored {@code LearningModule} that groups lessons. */
    MODULE,

    /** A {@code Lesson} (live or recorded). */
    LESSON,

    /** A {@code CoursePackage} that bundles modules and/or lessons. */
    PACKAGE;

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ManagedContentType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised content type
     */
    public static Optional<ManagedContentType> tryFrom(String value) {
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
     * or blank input throws "{@code Content type is required.}", invalid
     * input throws
     * "{@code Invalid content type. Allowed values are MODULE, LESSON, or PACKAGE.}".</p>
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised content type
     */
    public static ManagedContentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Content type is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid content type. Allowed values are MODULE, LESSON, or PACKAGE."));
    }

    /** @return {@code true} when this type denotes a {@link #MODULE}. */
    public boolean isModule() {
        return this == MODULE;
    }

    /** @return {@code true} when this type denotes a {@link #LESSON}. */
    public boolean isLesson() {
        return this == LESSON;
    }

    /** @return {@code true} when this type denotes a {@link #PACKAGE}. */
    public boolean isPackage() {
        return this == PACKAGE;
    }
}
