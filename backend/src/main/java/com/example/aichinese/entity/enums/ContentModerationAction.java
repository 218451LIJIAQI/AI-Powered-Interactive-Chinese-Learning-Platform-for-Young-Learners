package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Concrete moderation action recorded against a piece of platform content.
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code ContentModerationRecord}. The constant names are part of the
 * database contract — they MUST NOT be renamed or removed without a
 * paired Flyway migration and a sweep of admin tooling.</p>
 */
public enum ContentModerationAction {

    /** Soft-hide the content from public listings without removing it. */
    HIDE,

    /** Revert the content from {@code PUBLISHED} back to a non-public state. */
    UNPUBLISH,

    /** Move the content to {@code ARCHIVED} so it is no longer reachable. */
    ARCHIVE,

    /** Ask the original author to make changes before re-publishing. */
    REQUEST_REVISION,

    /** Reverse a previous restrictive action and re-expose the content. */
    RESTORE;

    /**
     * Restrictive actions that take content away from learners. Useful for
     * filtering audit logs and rendering badge severity in the moderation
     * dashboard.
     */
    public static final Set<ContentModerationAction> RESTRICTIVE =
            Set.of(HIDE, UNPUBLISH, ARCHIVE, REQUEST_REVISION);

    /** Restorative actions that re-expose previously restricted content. */
    public static final Set<ContentModerationAction> RESTORATIVE = Set.of(RESTORE);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ContentModerationAction> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised action
     */
    public static Optional<ContentModerationAction> tryFrom(String value) {
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
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised action
     */
    public static ContentModerationAction fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Moderation action is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid moderation action. Allowed values are HIDE, UNPUBLISH, ARCHIVE, "
                        + "REQUEST_REVISION, or RESTORE."));
    }

    /** @return {@code true} when the action takes content away from learners. */
    public boolean isRestrictive() {
        return RESTRICTIVE.contains(this);
    }

    /** @return {@code true} when the action re-exposes previously restricted content. */
    public boolean isRestorative() {
        return RESTORATIVE.contains(this);
    }
}
