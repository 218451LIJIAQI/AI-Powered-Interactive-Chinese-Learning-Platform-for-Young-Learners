package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * One badge the student has earned, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/rewards/badges} — paginated
 *       full earned-badge collection.</li>
 *   <li>Embedded inside {@link RewardSummaryResponse#badges()} as
 *       a recent-earned subset for the dashboard widget.</li>
 * </ul>
 *
 * <p>Carries one row per
 * {@link com.example.aichinese.entity.StudentBadge} (the join table
 * between {@code student_profiles} and
 * {@link com.example.aichinese.entity.Badge}). Badges that the
 * student has not earned do not appear in this list — there is no
 * "available but locked" view here; that is a separate concern
 * for the rewards-catalogue endpoint.</p>
 *
 * <h2>{@link #iconKey}</h2>
 * <p>Stable string key that the front-end maps to its locally
 * bundled icon set (e.g. {@code "streak-7-day"},
 * {@code "first-quiz-pass"}). The actual glyph lives in the
 * front-end so the platform can refresh artwork without a backend
 * deploy. Never blank for a published badge.</p>
 *
 * <p>{@link #awardedAt} is when this student earned the badge;
 * the front-end formats it as "Earned 3 days ago" relative to
 * now.</p>
 */
public record BadgeSummaryResponse(
        Long badgeId,
        String name,
        String description,
        String iconKey,
        LocalDateTime awardedAt) {
}
