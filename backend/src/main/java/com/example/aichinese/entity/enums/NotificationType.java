package com.example.aichinese.entity.enums;

public enum NotificationType {

    SYSTEM,

    TEACHER_FEEDBACK,

    PROGRESS_UPDATE,

    LIVE_CLASS_REMINDER,

    TEACHER_APPROVAL,

    CONTENT_REVIEW,

    MODERATION_ACTION,

    AI_REPORT_REVIEW,

    ADMIN_TASK,

    /** A support request was sent, answered or solved (Help & support, admin Support queue). */
    SUPPORT_REQUEST,

    /** A student joined one of the teacher's classes. */
    CLASS_ENROLMENT;
}
