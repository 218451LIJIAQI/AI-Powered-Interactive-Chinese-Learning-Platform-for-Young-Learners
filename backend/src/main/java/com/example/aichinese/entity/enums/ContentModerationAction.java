package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum ContentModerationAction {

    HIDE,

    UNPUBLISH,

    ARCHIVE,

    REQUEST_REVISION,

    RESTORE;

    public static final Set<ContentModerationAction> RESTRICTIVE = Collections.unmodifiableSet(
            EnumSet.of(HIDE, UNPUBLISH, ARCHIVE, REQUEST_REVISION));

    public static final Set<ContentModerationAction> RESTORATIVE =
            Collections.unmodifiableSet(EnumSet.of(RESTORE));

    static {
        EnumSet<ContentModerationAction> covered = EnumSet.copyOf(RESTRICTIVE);
        covered.addAll(RESTORATIVE);
        if (!covered.equals(EnumSet.allOf(ContentModerationAction.class))) {
            EnumSet<ContentModerationAction> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "ContentModerationAction category sets do not cover all constants; uncovered: " + missing);
        }
        EnumSet<ContentModerationAction> overlap = EnumSet.copyOf(RESTRICTIVE);
        overlap.retainAll(RESTORATIVE);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "ContentModerationAction RESTRICTIVE and RESTORATIVE sets must be disjoint; overlap: "
                            + overlap);
        }
    }

    private static final Map<String, ContentModerationAction> LOOKUP =
            EnumLookups.indexByName(ContentModerationAction.class);

    public static Optional<ContentModerationAction> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static ContentModerationAction fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Moderation action is required.",
                () -> "Invalid moderation action. Allowed values are HIDE, UNPUBLISH, ARCHIVE, "
                        + "REQUEST_REVISION, or RESTORE.");
    }

    public boolean isRestrictive() {
        return RESTRICTIVE.contains(this);
    }

    public boolean isRestorative() {
        return RESTORATIVE.contains(this);
    }
}
