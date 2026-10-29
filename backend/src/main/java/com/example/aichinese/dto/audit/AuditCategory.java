package com.example.aichinese.dto.audit;

import java.util.Locale;

/** The area an audit action belongs to, derived from its action type. */
public enum AuditCategory {

    ACCOUNT,

    CONTENT,

    AI,

    SUPPORT,

    LEARNING,

    SYSTEM;

    public static AuditCategory of(String actionType) {
        String action = actionType == null ? "" : actionType.toUpperCase(Locale.ROOT);
        if (action.startsWith("SUPPORT_")) {
            return SUPPORT;
        }
        if (action.startsWith("AI_") || action.contains("_AI_") || action.startsWith("ADMIN_PLATFORM_SETTINGS")) {
            return AI;
        }
        if (action.startsWith("CONTENT_") || action.startsWith("TEACHER_MODULE") || action.startsWith("TEACHER_LESSON")
                || action.startsWith("TEACHER_PACKAGE") || action.startsWith("TEACHER_QUIZ")
                || action.startsWith("TEACHER_ACTIVITY") || action.startsWith("ADMIN_ACTIVITY")
                || action.startsWith("TEACHER_RECORDED_VIDEO") || action.startsWith("MODERATION_")
                || action.startsWith("CLASS_") || action.startsWith("LESSON_") || action.startsWith("UNIT_")
                || action.startsWith("ADMIN_CLASS_") || action.startsWith("ADMIN_LESSON_") || action.startsWith("TEACHER_CLASS_")
                || action.startsWith("LEARN_") || action.startsWith("LEVEL_CHECK_") || action.startsWith("PRACTICE_SET_")) {
            return CONTENT;
        }
        if (action.startsWith("STUDENT_") || action.startsWith("TEACHER_LIVE_CLASS") || action.startsWith("TEACHER_LIVE_SESSION")
                || action.startsWith("TEACHER_FEEDBACK")
                || action.startsWith("PRACTICE_SUBMISSION") || action.startsWith("LIVE_SESSION")) {
            return LEARNING;
        }
        if (action.startsWith("ADMIN_USER") || action.startsWith("ADMIN_TEACHER") || action.startsWith("ADMIN_INVITE")
                || action.startsWith("TEACHER_APPLICATION") || action.startsWith("TEACHER_VERIFICATION")
                || action.startsWith("PASSWORD_") || action.startsWith("LOGIN") || action.startsWith("LOGOUT")
                || action.startsWith("PROFILE_") || action.startsWith("ACCOUNT_") || action.startsWith("EMAIL_")) {
            return ACCOUNT;
        }
        return SYSTEM;
    }
}
