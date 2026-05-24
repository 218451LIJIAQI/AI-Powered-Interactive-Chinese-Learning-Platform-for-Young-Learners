package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * One ledger entry from the student's XP transaction history,
 * returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/rewards/points-history} —
 *       paginated full ledger (newest first).</li>
 *   <li>Embedded inside
 *       {@link RewardSummaryResponse#recentPoints()} as the
 *       most-recent subset for the dashboard widget.</li>
 * </ul>
 *
 * <p>This is the per-event audit trail behind
 * {@link RewardSummaryResponse#totalPoints()}: every XP grant or
 * adjustment writes one row here, so the running total can be
 * reconstructed from the ledger. Surfacing the ledger to the
 * student lets them see exactly why their XP went up — a strong
 * motivational signal for child learners.</p>
 *
 * <h2>{@link #pointsDelta}</h2>
 * <p>Signed integer: positive for awards (quiz pass, lesson
 * complete, badge unlock, daily streak bonus), negative for
 * adjustments (admin clawback for proven cheating, etc.). The
 * front-end colours positive/negative differently.</p>
 *
 * <h2>{@link #sourceType} / {@link #sourceId}</h2>
 * <p>Together they pinpoint the activity that generated the XP.
 * {@link #sourceType} is a free-form discriminator like
 * {@code "QUIZ_ATTEMPT"}, {@code "FLASHCARD"},
 * {@code "MATCHING_GAME"}, {@code "LESSON_COMPLETE"},
 * {@code "DAILY_STREAK"}, etc.; {@link #sourceId} is the underlying
 * row id when applicable, or {@code null} for system grants like
 * streak bonuses. The front-end uses the pair to deep-link from
 * the ledger row back to the activity.</p>
 *
 * <p>{@link #reason} is the pre-formatted human label
 * ("Completed flashcard: 你好"); empty/null falls back to a
 * front-end default derived from {@link #sourceType}.</p>
 */
public record PointsTransactionResponse(
        Long id,
        Integer pointsDelta,
        String reason,
        String sourceType,
        Long sourceId,
        LocalDateTime createdAt) {
}
