package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * adjustments (admin clawback for proven cheating, refund
 * compensation, etc.). The front-end colours positive / negative
 * differently. Boxed {@link Integer} (not primitive) only because
 * the underlying entity column is mapped to a wrapper type — at
 * runtime it is always non-null per the schema's {@code NOT NULL}
 * constraint.</p>
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
 * ("Completed flashcard: 你好") and is non-null per the entity
 * schema ({@code reason} is {@code NOT NULL}); the front-end may
 * still fall back to a {@link #sourceType}-derived default if the
 * pre-formatted label is unhelpfully terse.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #pointsDelta}, {@link #reason} and
 *       {@link #createdAt} are non-{@code null} — they correspond
 *       to columns the schema declares {@code NOT NULL} (or to
 *       {@link com.example.aichinese.entity.base.BaseEntity}'s
 *       always-populated identity / timestamp fields).</li>
 *   <li>{@link #sourceType} and {@link #sourceId} may be
 *       {@code null} for system grants that do not link to a
 *       specific activity row (e.g. daily-streak bonuses).</li>
 * </ul>
 *
 * @param id          {@link com.example.aichinese.entity.PointsTransaction}
 *                    identifier (used as the React list key); never
 *                    {@code null}
 * @param pointsDelta signed XP delta (positive = award, negative =
 *                    adjustment); never {@code null}
 * @param reason      pre-formatted human label shown in the ledger
 *                    row; never {@code null}
 * @param sourceType  discriminator pinpointing the activity (e.g.
 *                    {@code "QUIZ_ATTEMPT"}); {@code null} for
 *                    system grants
 * @param sourceId    row id of the activity that generated the
 *                    grant; {@code null} when {@link #sourceType}
 *                    has no per-row backing
 * @param createdAt   wall-clock instant the ledger row was written
 *                    (Asia/Singapore zone); never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "pointsDelta",
        "reason",
        "sourceType",
        "sourceId",
        "createdAt"
})
public record PointsTransactionResponse(
        Long id,
        Integer pointsDelta,
        String reason,
        String sourceType,
        Long sourceId,
        LocalDateTime createdAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / signed-amount / display / timestamp fields per
     * the entity schema's {@code NOT NULL} constraints.
     * {@link #sourceType} and {@link #sourceId} are intentionally
     * permitted to be {@code null} per the invariants documented
     * above.
     */
    public PointsTransactionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(pointsDelta, "pointsDelta must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
