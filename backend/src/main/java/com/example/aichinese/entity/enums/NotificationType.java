package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum NotificationType {

    SYSTEM,

    TEACHER_FEEDBACK,

    QUIZ_RESULT,

    PROGRESS_UPDATE,

    REWARD_EARNED,

    PAYMENT_STATUS,

    REFUND_STATUS,

    PAYOUT_STATUS,

    LIVE_CLASS_REMINDER,

    TEACHER_APPROVAL,

    CONTENT_REVIEW,

    MODERATION_ACTION,

    AI_REPORT_REVIEW,

    ADMIN_TASK;

    public static final Set<NotificationType> COMMERCE = Collections.unmodifiableSet(EnumSet.of(
            PAYMENT_STATUS, REFUND_STATUS, PAYOUT_STATUS));

    public static final Set<NotificationType> ADMIN_FACING = Collections.unmodifiableSet(EnumSet.of(
            CONTENT_REVIEW, MODERATION_ACTION, AI_REPORT_REVIEW, ADMIN_TASK, TEACHER_APPROVAL));

    public static final Set<NotificationType> LEARNER_PEDAGOGICAL = Collections.unmodifiableSet(EnumSet.of(
            TEACHER_FEEDBACK, QUIZ_RESULT, PROGRESS_UPDATE, REWARD_EARNED, LIVE_CLASS_REMINDER));

    static {
        EnumSet<NotificationType> commerce = EnumSet.copyOf(COMMERCE);
        EnumSet<NotificationType> adminFacing = EnumSet.copyOf(ADMIN_FACING);
        EnumSet<NotificationType> learnerPedagogical = EnumSet.copyOf(LEARNER_PEDAGOGICAL);

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
        if (commerce.contains(SYSTEM) || adminFacing.contains(SYSTEM) || learnerPedagogical.contains(SYSTEM)) {
            throw new AssertionError(
                    "NotificationType SYSTEM is the residual fallback and must not be classified into "
                            + "COMMERCE / ADMIN_FACING / LEARNER_PEDAGOGICAL.");
        }
    }

    private static final Map<String, NotificationType> LOOKUP =
            EnumLookups.indexByName(NotificationType.class);

    public static Optional<NotificationType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

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

    public static NotificationType fromValueOrSystem(String value) {
        return tryFrom(value).orElse(SYSTEM);
    }

    public boolean isCommerce() {
        return COMMERCE.contains(this);
    }

    public boolean isAdminFacing() {
        return ADMIN_FACING.contains(this);
    }

    public boolean isLearnerPedagogical() {
        return LEARNER_PEDAGOGICAL.contains(this);
    }
}
