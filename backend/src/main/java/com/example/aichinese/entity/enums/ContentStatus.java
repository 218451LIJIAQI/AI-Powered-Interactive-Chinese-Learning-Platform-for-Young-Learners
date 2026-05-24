package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Editorial lifecycle of a teacher- or admin-managed piece of learning
 * content (lesson, module, package, quiz, activity).
 *
 * <p>Originally designed around an admin-review workflow
 * ({@link #SUBMITTED} → {@link #APPROVED}/{@link #REJECTED} →
 * {@link #PUBLISHED}). Teacher-owned content now goes live immediately
 * (no review), but the older states are retained so historic rows
 * remain valid and admins can still moderate (see
 * {@code TeacherContentAccessService} and {@code AdminContentReviewService}).</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on numerous
 * content entities. The constant names are part of the database
 * contract and appear in the {@code content_status} ENUM column on
 * eight tables — they MUST NOT be renamed or removed without a paired
 * Flyway migration that updates every affected table.</p>
 */
public enum ContentStatus {

    /** Author is still composing; not visible to anyone but the author. */
    DRAFT,

    /** Author submitted to admin review; pending approval (legacy flow). */
    SUBMITTED,

    /** Admin approved but not yet published (legacy flow). */
    APPROVED,

    /** Admin declined; author may revise and resubmit. */
    REJECTED,

    /** Visible to learners according to the content's access tier. */
    PUBLISHED,

    /** No longer offered to learners; preserved for historical reference. */
    ARCHIVED;

    /**
     * Statuses that an approved teacher may edit. Single source of truth
     * mirrored by {@code TeacherContentAccessService.TEACHER_EDITABLE_STATUSES}.
     */
    public static final Set<ContentStatus> TEACHER_EDITABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, PUBLISHED, REJECTED));

    /**
     * Statuses that may be archived by the owning teacher. Currently
     * identical to {@link #TEACHER_EDITABLE} by design — the two sets
     * are kept separate so each policy can evolve independently (e.g.
     * an admin might later allow archiving {@code SUBMITTED} content
     * while forbidding edits to it).
     */
    public static final Set<ContentStatus> TEACHER_ARCHIVABLE =
            Collections.unmodifiableSet(EnumSet.of(DRAFT, PUBLISHED, REJECTED));

    /** Statuses that surface to learners through the public catalogue. */
    public static final Set<ContentStatus> PUBLIC_VISIBLE =
            Collections.unmodifiableSet(EnumSet.of(PUBLISHED));

    /**
     * Statuses that count as "in review" for admin dashboards (legacy flow,
     * still relevant for AI-generated or migrated content).
     */
    public static final Set<ContentStatus> IN_REVIEW =
            Collections.unmodifiableSet(EnumSet.of(SUBMITTED, APPROVED));

    /** Statuses that have reached the end of the editorial lifecycle. */
    public static final Set<ContentStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(ARCHIVED));

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, ContentStatus> LOOKUP =
            EnumLookups.indexByName(ContentStatus.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised content status
     */
    public static Optional<ContentStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised content status
     */
    public static ContentStatus fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Content status is required.",
                () -> "Invalid content status. Allowed values are DRAFT, SUBMITTED, APPROVED, "
                        + "REJECTED, PUBLISHED, or ARCHIVED.");
    }

    /** @return {@code true} when the content is visible in the public catalogue. */
    public boolean isPublished() {
        return this == PUBLISHED;
    }

    /** @return {@code true} when the content is still being authored. */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /** @return {@code true} when the content has been retired from the catalogue. */
    public boolean isArchived() {
        return this == ARCHIVED;
    }

    /** @return {@code true} when admin review declined the content. */
    public boolean isRejected() {
        return this == REJECTED;
    }

    /** @return {@code true} when the owning teacher may currently edit the content. */
    public boolean isTeacherEditable() {
        return TEACHER_EDITABLE.contains(this);
    }

    /** @return {@code true} when the owning teacher may currently archive the content. */
    public boolean isTeacherArchivable() {
        return TEACHER_ARCHIVABLE.contains(this);
    }

    /** @return {@code true} when learners may discover the content via public listings. */
    public boolean isPublicVisible() {
        return PUBLIC_VISIBLE.contains(this);
    }
}
