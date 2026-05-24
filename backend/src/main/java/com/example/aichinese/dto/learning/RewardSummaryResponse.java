package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Aggregate reward dashboard payload returned by
 * {@code GET /api/v1/students/rewards}.
 *
 * <p>Bundles every reward-related concern (XP total, level, streak,
 * earned badges, recent XP grants) into one round-trip so the
 * student's reward dashboard can hydrate from a single response.
 * Each individual concern also has its own dedicated endpoint for
 * paginated drill-down:</p>
 *
 * <ul>
 *   <li>{@code /rewards/badges} → paginated
 *       {@link BadgeSummaryResponse}</li>
 *   <li>{@code /rewards/points-history} → paginated
 *       {@link PointsTransactionResponse}</li>
 *   <li>{@code /rewards/streak} →
 *       {@link DailyStreakResponse}</li>
 * </ul>
 *
 * <h2>Headline fields</h2>
 * <ul>
 *   <li>{@link #totalPoints} — student's running XP, the running
 *       total over {@link PointsTransactionResponse#pointsDelta()}.</li>
 *   <li>{@link #currentLevelNumber} — derived from total XP via the
 *       platform's level-curve table; rises monotonically. Always
 *       {@code &gt;= 1}.</li>
 *   <li>{@link #progressToNextLevelPercent} — 0–100 percentage of
 *       the way to the next level; resets to 0 on level-up.</li>
 * </ul>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #streak} — full {@link DailyStreakResponse} so the
 *       streak indicator can render without a second request.</li>
 *   <li>{@link #badges} — most-recent earned badges (server-capped
 *       to a small N to keep the dashboard payload compact);
 *       defensively copied so callers may mutate the original
 *       input collection without affecting the bound payload.</li>
 *   <li>{@link #recentPoints} — most-recent XP transactions
 *       (server-capped) for the "Recent XP" feed; defensively
 *       copied.</li>
 * </ul>
 *
 * <p>Primitive {@code int} for the three counters so the JSON
 * wire format always emits numeric values.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #totalPoints} is non-negative.</li>
 *   <li>{@link #currentLevelNumber} is {@code &gt;= 1} (a student
 *       always begins at level 1; the canonical constructor
 *       clamps lower values up to 1).</li>
 *   <li>{@link #progressToNextLevelPercent} is in {@code [0, 100]}
 *       — defensively clamped by the canonical constructor.</li>
 *   <li>{@link #badges} and {@link #recentPoints} are non-{@code null}
 *       (empty list rather than {@code null}).</li>
 * </ul>
 *
 * @param totalPoints                 running XP balance for the
 *                                    student; non-negative
 * @param currentLevelNumber          derived level from the XP
 *                                    curve; {@code &gt;= 1}
 * @param progressToNextLevelPercent  0–100 progress to next level;
 *                                    defensively clamped
 * @param streak                      embedded {@link DailyStreakResponse};
 *                                    nominally non-null
 * @param badges                      recent earned badges
 *                                    (defensively copied); never
 *                                    {@code null}
 * @param recentPoints                recent XP transactions
 *                                    (defensively copied); never
 *                                    {@code null}
 */
@JsonPropertyOrder({
        "totalPoints",
        "currentLevelNumber",
        "progressToNextLevelPercent",
        "streak",
        "badges",
        "recentPoints"
})
public record RewardSummaryResponse(
        int totalPoints,
        int currentLevelNumber,
        int progressToNextLevelPercent,
        DailyStreakResponse streak,
        List<BadgeSummaryResponse> badges,
        List<PointsTransactionResponse> recentPoints) {

    /**
     * Defensive canonical constructor: enforces the non-negative
     * {@link #totalPoints} invariant; clamps {@link #currentLevelNumber}
     * to {@code &gt;= 1} (a regression that produced level 0 would
     * crash the front-end level-badge component);
     * clamps {@link #progressToNextLevelPercent} into the {@code [0, 100]}
     * window (a regression in the level-curve formula that emitted
     * {@code 101} would skew the radial progress widget); and
     * defensively copies the list fields (with {@code null} →
     * {@link List#of()} normalisation) so external mutation cannot
     * leak into the bound payload after construction.
     */
    public RewardSummaryResponse {
        if (totalPoints < 0) {
            throw new IllegalArgumentException("totalPoints must be non-negative, was " + totalPoints);
        }
        currentLevelNumber = Math.max(1, currentLevelNumber);
        progressToNextLevelPercent = Math.max(0, Math.min(100, progressToNextLevelPercent));
        badges = badges == null ? List.of() : List.copyOf(badges);
        recentPoints = recentPoints == null ? List.of() : List.copyOf(recentPoints);
    }
}
