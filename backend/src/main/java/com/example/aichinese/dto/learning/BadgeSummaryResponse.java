package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * deploy. May be {@code null} for legacy badges authored before
 * the icon-key column was introduced; the front-end falls back to
 * a generic award glyph in that case.</p>
 *
 * <p>{@link #awardedAt} is when this student earned the badge;
 * the front-end formats it as "Earned 3 days ago" relative to
 * now.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #badgeId}, {@link #name} and {@link #awardedAt} are
 *       non-{@code null} — they are the columns the rewards widget
 *       renders unconditionally and the deep-link uses; every row
 *       projected from {@link com.example.aichinese.entity.StudentBadge}
 *       is guaranteed to have them by the schema (badge name is
 *       {@code NOT NULL UNIQUE}; awarded-at is {@code NOT NULL}).</li>
 *   <li>{@link #description} and {@link #iconKey} may be
 *       {@code null} — both columns are nullable on
 *       {@link com.example.aichinese.entity.Badge} and curators
 *       sometimes ship a badge without a long description.</li>
 * </ul>
 *
 * @param badgeId    the underlying {@link com.example.aichinese.entity.Badge}
 *                   identifier (used as the React list key on the
 *                   front-end and for "view full badge detail"
 *                   deep-links); never {@code null}
 * @param name       short human-readable badge title (e.g.
 *                   {@code "Seven-Day Streak"}); never {@code null}
 * @param description optional one-line elaboration shown beneath the
 *                    badge name in the rewards drawer; may be
 *                    {@code null} for badges shipped without prose
 * @param iconKey    stable front-end-resolved icon key (see class
 *                   Javadoc); may be {@code null} for legacy badges
 *                   authored before the column was introduced
 * @param awardedAt  wall-clock instant the student earned this badge
 *                   (Asia/Singapore zone; see
 *                   {@link com.example.aichinese.entity.base.BaseEntity}
 *                   Javadoc); never {@code null}
 */
@JsonPropertyOrder({ "badgeId", "name", "description", "iconKey", "awardedAt" })
public record BadgeSummaryResponse(
        Long badgeId,
        String name,
        String description,
        String iconKey,
        LocalDateTime awardedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / timestamp fields the rewards widget
     * renders unconditionally. {@link #description} and
     * {@link #iconKey} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public BadgeSummaryResponse {
        Objects.requireNonNull(badgeId, "badgeId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(awardedAt, "awardedAt must not be null");
    }
}
