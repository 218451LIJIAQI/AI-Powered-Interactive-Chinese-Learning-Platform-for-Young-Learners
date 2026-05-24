package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * Compact lesson-progress row, surfaced inside
 * {@link StudentProgressSummaryResponse#recentLessons()}.
 *
 * <p>Drives the "Continue learning" / "Recent lessons" section on
 * the student dashboard. Always sorted by
 * {@link #lastAccessedAt DESC} (most recent first) and capped to a
 * small N (typically 5-10) by the service so the dashboard payload
 * stays compact.</p>
 *
 * <p>{@link #completionPercent} mirrors
 * {@link StudentLessonPlayerResponse#completionPercent()};
 * {@link #completed} is the derived
 * {@code completionPercent >= 100} flag (primitive {@code boolean}
 * so the JSON wire format always emits a value).</p>
 */
public record StudentProgressLessonResponse(
        Long lessonId,
        String lessonTitle,
        int completionPercent,
        boolean completed,
        LocalDateTime lastAccessedAt) {
}
