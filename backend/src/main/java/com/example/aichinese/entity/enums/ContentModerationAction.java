package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    public static final Set<ContentModerationAction> RESTRICTIVE = Collections.unmodifiableSet(
            EnumSet.of(HIDE, UNPUBLISH, ARCHIVE, REQUEST_REVISION));

    /** Restorative actions that re-expose previously restricted content. */
    public static final Set<ContentModerationAction> RESTORATIVE =
            Collections.unmodifiableSet(EnumSet.of(RESTORE));

    /*
     * Startup-time exhaustiveness guard: every constant must be either
     * restrictive or restorative.  Catches forgotten classification at
     * class-load time rather than at runtime.
     */
    static {
        EnumSet<ContentModerationAction> covered = EnumSet.copyOf(RESTRICTIVE);
        covered.addAll(RESTORATIVE);
        if (!covered.equals(EnumSet.allOf(ContentModerationAction.class))) {
            EnumSet<ContentModerationAction> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "ContentModerationAction category sets do not cover all constants; uncovered: " + missing);
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ContentModerationAction> LOOKUP =
            EnumLookups.indexByName(ContentModerationAction.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised action
     */
    public static Optional<ContentModerationAction> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised action
     */
    public static ContentModerationAction fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Moderation action is required.",
                () -> "Invalid moderation action. Allowed values are HIDE, UNPUBLISH, ARCHIVE, "
                        + "REQUEST_REVISION, or RESTORE.");
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
