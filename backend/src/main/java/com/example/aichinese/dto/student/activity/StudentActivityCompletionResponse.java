package com.example.aichinese.dto.student.activity;

import java.util.Objects;

import com.example.aichinese.dto.admin.activity.ActivityType;

public record StudentActivityCompletionResponse(
        Long activityId,
        ActivityType type,
        int earnedPoints,
        int totalPoints,
        boolean firstCompletion) {

    public StudentActivityCompletionResponse {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        requireNonNegative(earnedPoints, "earnedPoints");
        requireNonNegative(totalPoints, "totalPoints");
        if (!firstCompletion && earnedPoints != 0) {
            throw new IllegalArgumentException(
                    "earnedPoints must be 0 on a non-first completion (replay), got " + earnedPoints);
        }
    }

    public static StudentActivityCompletionResponse firstCompletion(
            Long activityId,
            ActivityType type,
            int earnedPoints,
            int totalPoints) {
        return new StudentActivityCompletionResponse(activityId, type, earnedPoints, totalPoints, true);
    }

    public static StudentActivityCompletionResponse replay(
            Long activityId,
            ActivityType type,
            int totalPoints) {
        return new StudentActivityCompletionResponse(activityId, type, 0, totalPoints, false);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, got " + value);
        }
    }
}
