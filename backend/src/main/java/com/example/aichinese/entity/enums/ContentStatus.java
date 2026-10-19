package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Review workflow state of courses, modules, lessons, quizzes and practice activities (D4, D14, D24).
 *
 * <pre>
 * DRAFT --submit--> SUBMITTED --approve--> PUBLISHED --archive--> ARCHIVED --restore--> PUBLISHED
 *   ^                   |                      |
 *   |                   +--reject(reason)--> REJECTED --edit/submit--> SUBMITTED
 *   |                                          ^
 *   +-- create                                 +-- reject / request revision of PUBLISHED content
 * PUBLISHED --teacher edit--> SUBMITTED (re-review; hidden from students until approved)
 * </pre>
 *
 * <p>{@link #APPROVED} is kept only so the existing database enum columns stay compatible. It is
 * never assigned: V4 migrated every APPROVED row (the old "hidden by moderation" state) to
 * ARCHIVED, from which an admin can restore it.</p>
 */
public enum ContentStatus {

    DRAFT,

    SUBMITTED,

    /** Legacy value kept for database compatibility; never assigned (see the class comment). */
    APPROVED,

    REJECTED,

    PUBLISHED,

    ARCHIVED;

    /** Teachers may edit these; editing PUBLISHED content sends it back to SUBMITTED. */
    public static final Set<ContentStatus> TEACHER_EDITABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, PUBLISHED, REJECTED));

    public static final Set<ContentStatus> TEACHER_ARCHIVABLE = TEACHER_EDITABLE;

    /** Teachers may submit these for review. */
    public static final Set<ContentStatus> TEACHER_SUBMITTABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, REJECTED));

    public static final Set<ContentStatus> PUBLIC_VISIBLE =
            Collections.unmodifiableSet(EnumSet.of(PUBLISHED));

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

    public boolean isPublicVisible() {
        return PUBLIC_VISIBLE.contains(this);
    }
}
