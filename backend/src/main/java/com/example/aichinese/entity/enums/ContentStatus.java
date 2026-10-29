package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Review workflow state of courses, modules, lessons, quizzes and practice activities (FRCONT06).
 *
 * <pre>
 * DRAFT --submit--> SUBMITTED --approve--> PUBLISHED --archive--> ARCHIVED --restore--> status before archive
 *   ^                   |                      |
 *   |                   +--reject(reason)--> REJECTED --edit/submit--> SUBMITTED
 *   |                                          ^
 *   +-- create                                 +-- reject / request revision of PUBLISHED content
 * PUBLISHED --teacher edit--> SUBMITTED (re-review; hidden from students until approved)
 * DRAFT / REJECTED --teacher archive--> ARCHIVED (restore returns them to the teacher, unpublished;
 *   rows archived before V8 have no stored status and restore to PUBLISHED)
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

    /** Teachers may archive their own content in these states (submitted content waits for the admin's decision). */
    public static final Set<ContentStatus> TEACHER_ARCHIVABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, PUBLISHED, REJECTED));

    /** The status an archived item is restored to: the one it had before it was archived, else Published. */
    public static ContentStatus afterRestore(ContentStatus before) {
        return before == null || before == ARCHIVED ? PUBLISHED : before;
    }

    public static Optional<ContentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(ContentStatus.class, value);
    }
}
