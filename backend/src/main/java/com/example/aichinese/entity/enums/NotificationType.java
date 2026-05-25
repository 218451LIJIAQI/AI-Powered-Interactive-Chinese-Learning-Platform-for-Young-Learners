package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Topical category of an in-app notification.
 *
 * <p>Drives icon rendering in {@code NotificationIconRenderer}, audience
 * routing inside {@code NotificationService}, and analytics roll-ups.</p>
 *
 * <p>Persisted with {@code @Enumerated(EnumType.STRING)} on
 * {@code Notification}. The constant set is asserted by
 * {@code SchemaEnumContractTests#latestNotificationTypeDatabaseEnumContainsEveryApplicationValue}
 * against the {@code notifications.notification_type} MySQL ENUM
 * defined in the latest Flyway migration — any change MUST be paired
 * with a Flyway migration that updates the column definition.</p>
 */
public enum NotificationType {

    /** Generic platform-wide announcement (release notes, maintenance, ToS update). */
    SYSTEM,

    /** A teacher posted free-text feedback for the learner. */
    TEACHER_FEEDBACK,

    /** A quiz attempt has been graded; results are available to view. */
    QUIZ_RESULT,

    /** Periodic learning-progress digest (weekly streaks, lesson completion). */
    PROGRESS_UPDATE,

    /** Learner earned a badge, points milestone, or other reward. */
    REWARD_EARNED,

    /** State change on an order / payment record (e.g. PAID, FAILED, CANCELLED). */
    PAYMENT_STATUS,

    /** State change on a refund request (PENDING / APPROVED / REJECTED / REFUNDED). */
    REFUND_STATUS,

    /** State change on a teacher payout request (PENDING / APPROVED / REJECTED / PAID). */
    PAYOUT_STATUS,

    /** Reminder that a scheduled live class is about to start. */
    LIVE_CLASS_REMINDER,

    /** Teacher verification was approved or rejected. */
    TEACHER_APPROVAL,

    /** Content submission was reviewed (legacy admin-review flow). */
    CONTENT_REVIEW,

    /** Moderation action (hide, archive, restore, etc.) was applied to content. */
    MODERATION_ACTION,

    /** AI response report was reviewed and resolved or dismissed. */
    AI_REPORT_REVIEW,

    /** Administrative escalation (e.g. new abuse report, queue overflow). */
    ADMIN_TASK;

    /** Notifications related to commerce / accounting flows. */
    public static final Set<NotificationType> COMMERCE = Collections.unmodifiableSet(EnumSet.of(
            PAYMENT_STATUS, REFUND_STATUS, PAYOUT_STATUS));

    /** Notifications that require admin attention rather than learner attention. */
    public static final Set<NotificationType> ADMIN_FACING = Collections.unmodifiableSet(EnumSet.of(
            CONTENT_REVIEW, MODERATION_ACTION, AI_REPORT_REVIEW, ADMIN_TASK, TEACHER_APPROVAL));

    /** Notifications that relate to the learner's own pedagogical journey. */
    public static final Set<NotificationType> LEARNER_PEDAGOGICAL = Collections.unmodifiableSet(EnumSet.of(
            TEACHER_FEEDBACK, QUIZ_RESULT, PROGRESS_UPDATE, REWARD_EARNED, LIVE_CLASS_REMINDER));

    /*
     * Startup-time invariant guard.  This enum has *three* category sets
     * (commerce / admin-facing / learner-pedagogical) plus exactly one
     * residual constant ({@link #SYSTEM}) that intentionally belongs to
     * none of them.  Two invariants must hold:
     *
     *   1. The three category sets are pairwise disjoint.  Otherwise
     *      isCommerce() / isAdminFacing() / isLearnerPedagogical()
     *      would no longer be mutually exclusive predicates and a
     *      single notification could be routed to multiple inboxes /
     *      double-counted in analytics dashboards.
     *
     *   2. Their union equals {@code ALL \ {SYSTEM}}.  Otherwise a
     *      newly-added unclassified constant would silently produce
     *      {@code false} from every predicate and "disappear" from
     *      every dashboard's category roll-up.
     *
     * Both checks run exactly once at class load and fail fast with a
     * clear AssertionError naming the offending constants — a future
     * contributor sees the problem before any tests run.
     */
    static {
        EnumSet<NotificationType> commerce = EnumSet.copyOf(COMMERCE);
        EnumSet<NotificationType> adminFacing = EnumSet.copyOf(ADMIN_FACING);
        EnumSet<NotificationType> learnerPedagogical = EnumSet.copyOf(LEARNER_PEDAGOGICAL);

        // Pairwise disjointness.
        EnumSet<NotificationType> commerceAdmin = EnumSet.copyOf(commerce);
        commerceAdmin.retainAll(adminFacing);
        if (!commerceAdmin.isEmpty()) {
            throw new AssertionError(
                    "NotificationType COMMERCE and ADMIN_FACING sets must be disjoint; overlap: "
                            + commerceAdmin);
        }
        EnumSet<NotificationType> commerceLearner = EnumSet.copyOf(commerce);
        commerceLearner.retainAll(learnerPedagogical);
        if (!commerceLearner.isEmpty()) {
            throw new AssertionError(
                    "NotificationType COMMERCE and LEARNER_PEDAGOGICAL sets must be disjoint; overlap: "
                            + commerceLearner);
        }
        EnumSet<NotificationType> adminLearner = EnumSet.copyOf(adminFacing);
        adminLearner.retainAll(learnerPedagogical);
        if (!adminLearner.isEmpty()) {
            throw new AssertionError(
                    "NotificationType ADMIN_FACING and LEARNER_PEDAGOGICAL sets must be disjoint; overlap: "
                            + adminLearner);
        }

        // Coverage: union of categories + {SYSTEM} must equal the full enum.
        EnumSet<NotificationType> classified = EnumSet.copyOf(commerce);
        classified.addAll(adminFacing);
        classified.addAll(learnerPedagogical);
        classified.add(SYSTEM);
        if (!classified.equals(EnumSet.allOf(NotificationType.class))) {
            EnumSet<NotificationType> missing = EnumSet.complementOf(classified);
            throw new AssertionError(
                    "NotificationType is missing a category classification (and is not SYSTEM); "
                            + "uncovered: " + missing);
        }
        // SYSTEM specifically must NOT appear in any category set, since it
        // is documented as the residual fallback used by fromValueOrSystem().
        if (commerce.contains(SYSTEM) || adminFacing.contains(SYSTEM) || learnerPedagogical.contains(SYSTEM)) {
            throw new AssertionError(
                    "NotificationType SYSTEM is the residual fallback and must not be classified into "
                            + "COMMERCE / ADMIN_FACING / LEARNER_PEDAGOGICAL.");
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, NotificationType> LOOKUP =
            EnumLookups.indexByName(NotificationType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised type
     */
    public static Optional<NotificationType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised type
     */
    public static NotificationType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Notification type is required.",
                () -> "Invalid notification type. Allowed values are SYSTEM, TEACHER_FEEDBACK, "
                        + "QUIZ_RESULT, PROGRESS_UPDATE, REWARD_EARNED, PAYMENT_STATUS, "
                        + "REFUND_STATUS, PAYOUT_STATUS, LIVE_CLASS_REMINDER, TEACHER_APPROVAL, "
                        + "CONTENT_REVIEW, MODERATION_ACTION, AI_REPORT_REVIEW, or ADMIN_TASK.");
    }

    /**
     * Lenient counterpart that falls back to {@link #SYSTEM} when the
     * input is missing or unrecognised. Suitable for legacy import paths
     * where a missing type should not break notification creation.
     */
    public static NotificationType fromValueOrSystem(String value) {
        return tryFrom(value).orElse(SYSTEM);
    }

    /** @return {@code true} when this notification belongs to a commerce flow. */
    public boolean isCommerce() {
        return COMMERCE.contains(this);
    }

    /** @return {@code true} when this notification primarily targets administrators. */
    public boolean isAdminFacing() {
        return ADMIN_FACING.contains(this);
    }

    /** @return {@code true} when this notification relates to the learner's progress. */
    public boolean isLearnerPedagogical() {
        return LEARNER_PEDAGOGICAL.contains(this);
    }
}
