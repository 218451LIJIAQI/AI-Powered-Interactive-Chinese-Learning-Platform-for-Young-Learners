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
