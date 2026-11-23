package com.example.aichinese.entity.enums;

public enum NotificationType {

    /** About the account itself (reactivated by an admin). */
    SYSTEM,

    /** A learner's AI report was reviewed. */
    AI_REPORT_REVIEW,

    /** Work waiting for the admins (an AI report to review). */
    ADMIN_TASK,

    /** A new support request for the admins' Support queue. */
    SUPPORT_REQUEST,

    /** A learner's XP passed a rank threshold ("You reached Rank 3"). */
    RANK_REACHED,

    /** An admin published a new Practice set ("New in Practice"). */
    NEW_PRACTICE_SET,

    /** An admin published changes to a lesson of a class the learner joined ("Lesson updated"). */
    LESSON_UPDATED,

    /** The evening reminder of a learner with a plan who has not reached today's goal. */
    DAILY_GOAL_REMINDER,

    /** The weekly reminder of a learner with mistakes waiting. */
    MISTAKES_TO_REVIEW;
}
