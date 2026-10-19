package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.admin.activity.ActivityType;

/**
 * Server-validated result of an activity attempt. XP and the completion record are created only
 * when {@code passed} and only the first time ({@code firstCompletion}); a failed attempt reports
 * which items were wrong without revealing the answers. Explanations are returned only when passed.
 */
public record StudentActivityCompletionResponse(
        Long activityId,
        ActivityType type,
        boolean passed,
        int correctCount,
        int totalCount,
        int earnedPoints,
        int totalPoints,
        boolean firstCompletion,
        List<ItemResult> results) {

    public StudentActivityCompletionResponse {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        requireNonNegative(earnedPoints, "earnedPoints");
        requireNonNegative(totalPoints, "totalPoints");
        requireNonNegative(correctCount, "correctCount");
        requireNonNegative(totalCount, "totalCount");
        if (!firstCompletion && earnedPoints != 0) {
            throw new IllegalArgumentException(
                    "earnedPoints must be 0 unless this is the first passed completion, got " + earnedPoints);
        }
        if (firstCompletion && !passed) {
            throw new IllegalArgumentException("Only a passed attempt can be a first completion.");
        }
        results = results == null ? List.of() : List.copyOf(results);
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative, got " + value);
        }
    }

    /** Correctness of one item (left item id, drag item id, question index or question id). */
    public record ItemResult(String key, boolean correct, String explanation) {

        public ItemResult {
            Objects.requireNonNull(key, "key must not be null");
        }
    }
}