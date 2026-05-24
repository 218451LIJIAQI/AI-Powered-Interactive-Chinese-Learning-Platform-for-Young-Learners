package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * {@link StudentLessonPlayerResponse#completionPercent()} and is
 * clamped to {@code [0, 100]} by the canonical constructor;
 * {@link #completed} is the derived
 * {@code completionPercent >= 100} flag (primitive {@code boolean}
 * so the JSON wire format always emits a value).</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #lessonId} and {@link #lessonTitle} are
 *       non-{@code null}.</li>
 *   <li>{@link #completionPercent} is in {@code [0, 100]}.</li>
 *   <li>{@link #lastAccessedAt} may be {@code null} only for legacy
 *       rows authored before the column was always-populated.</li>
 * </ul>
 *
 * @param lessonId          {@code Lesson} identifier; never
 *                          {@code null}
 * @param lessonTitle       human-readable lesson title; never
 *                          {@code null}
 * @param completionPercent 0–100 lesson completion; defensively
 *                          clamped
 * @param completed         derived
 *                          {@code completionPercent &gt;= 100} flag
 * @param lastAccessedAt    most recent activity instant on this
 *                          lesson; may be {@code null} for legacy
 *                          rows
 */
@JsonPropertyOrder({ "lessonId", "lessonTitle", "completionPercent", "completed", "lastAccessedAt" })
public record StudentProgressLessonResponse(
        Long lessonId,
        String lessonTitle,
        int completionPercent,
        boolean completed,
        LocalDateTime lastAccessedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the dashboard "Continue learning"
     * card renders unconditionally; clamps
     * {@link #completionPercent} into the {@code [0, 100]} window
     * so the front-end radial progress widget cannot overflow.
     */
    public StudentProgressLessonResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
    }
}
