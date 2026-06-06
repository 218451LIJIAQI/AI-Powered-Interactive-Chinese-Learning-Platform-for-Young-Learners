package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum ContentStatus {

    DRAFT,

    SUBMITTED,

    APPROVED,

    REJECTED,

    PUBLISHED,

    ARCHIVED;

    public static final Set<ContentStatus> TEACHER_EDITABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, PUBLISHED, REJECTED));

    public static final Set<ContentStatus> TEACHER_ARCHIVABLE = TEACHER_EDITABLE;

    public static final Set<ContentStatus> PUBLIC_VISIBLE =
            Collections.unmodifiableSet(EnumSet.of(PUBLISHED));

    public static final Set<ContentStatus> IN_REVIEW =
            Collections.unmodifiableSet(EnumSet.of(SUBMITTED, APPROVED));

    public static final Set<ContentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(ARCHIVED));

    private static final Map<String, ContentStatus> LOOKUP =
            EnumLookups.indexByName(ContentStatus.class);

    public static Optional<ContentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static ContentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Content status is required.",
                () -> "Invalid content status. Allowed values are DRAFT, SUBMITTED, APPROVED, "
                        + "REJECTED, PUBLISHED, or ARCHIVED.");
    }

    public boolean isPublished() {
        return this == PUBLISHED;
    }

    public boolean isDraft() {
        return this == DRAFT;
    }

    public boolean isArchived() {
        return this == ARCHIVED;
    }

    public boolean isRejected() {
        return this == REJECTED;
    }

    public boolean isTeacherEditable() {
        return TEACHER_EDITABLE.contains(this);
    }

    public boolean isTeacherArchivable() {
        return TEACHER_ARCHIVABLE.contains(this);
    }

    public boolean isPublicVisible() {
        return PUBLIC_VISIBLE.contains(this);
    }
}
