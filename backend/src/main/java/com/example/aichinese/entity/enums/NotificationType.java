package com.example.aichinese.entity.enums;

import java.util.Map;
import java.util.Optional;

public enum NotificationType {

    SYSTEM,

    TEACHER_FEEDBACK,

    QUIZ_RESULT,

    PROGRESS_UPDATE,

    REWARD_EARNED,

    LIVE_CLASS_REMINDER,

    TEACHER_APPROVAL,

    CONTENT_REVIEW,

    MODERATION_ACTION,

    AI_REPORT_REVIEW,

    ADMIN_TASK;

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
                        + "QUIZ_RESULT, PROGRESS_UPDATE, REWARD_EARNED, LIVE_CLASS_REMINDER, "
                        + "TEACHER_APPROVAL, CONTENT_REVIEW, MODERATION_ACTION, AI_REPORT_REVIEW, or ADMIN_TASK.");
    }
}
