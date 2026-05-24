package com.example.aichinese.dto.learning;

import java.util.List;

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
 *       platform's level-curve table; rises monotonically.</li>
 *   <li>{@link #progressToNextLevelPercent} — 0-100 percentage of
 *       the way to the next level; resets to 0 on level-up.</li>
 * </ul>
 *
 * <h2>Embedded subsets</h2>
 * <ul>
 *   <li>{@link #streak} — full {@link DailyStreakResponse} so the
 *       streak indicator can render without a second request.</li>
 *   <li>{@link #badges} — most-recent earned badges (server-capped
 *       to a small N to keep the dashboard payload compact).</li>
 *   <li>{@link #recentPoints} — most-recent XP transactions
 *       (server-capped) for the "Recent XP" feed.</li>
 * </ul>
 *
 * <p>Primitive {@code int} for the three counters so the JSON
 * wire format always emits numeric values.</p>
 */
public record RewardSummaryResponse(
        int totalPoints,
        int currentLevelNumber,
        int progressToNextLevelPercent,
        DailyStreakResponse streak,
        List<BadgeSummaryResponse> badges,
        List<PointsTransactionResponse> recentPoints) {
}
