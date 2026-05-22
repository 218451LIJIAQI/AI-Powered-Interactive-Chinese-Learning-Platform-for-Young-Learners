package com.example.aichinese.dto.student.activity;

import com.example.aichinese.dto.admin.activity.ActivityType;

/**
 * Result of {@code POST /api/v1/students/activities/{type}/{id}/complete}.
 * Tells the student-facing UI how many points were just earned, the new
 * running total, and whether this was the first time this activity counted
 * towards the student's XP (so the UI can show a celebration only on the
 * very first successful completion).
 */
public record StudentActivityCompletionResponse(
        Long activityId,
        ActivityType type,
        int earnedPoints,
        int totalPoints,
        boolean firstCompletion) {
}
