package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One row of the teacher-facing video-watch report; surfaced by the
 * teacher dashboard's "Who is watching what" widget.
 *
 * <p>Joins per-student metadata
 * ({@link #studentId}, {@link #studentName}, {@link #studentEmail},
 * denormalised from {@code users}) with the corresponding
 * {@link com.example.aichinese.entity.VideoProgress} row so
 * the teacher can see at a glance who has finished which recording
 * — and follow up directly via the email link if needed.</p>
 *
 * <p>{@link #lastWatchedAt} is typed as {@code String} (not
 * {@code LocalDateTime}) because the producer surfaces the raw
 * ISO-8601 representation via {@code LocalDateTime#toString()} —
 * the front-end is then responsible for converting that to a
 * "X minutes ago" / locale-specific label. {@code null} (not the
 * empty string) means "never watched"; clients should null-check.
 * Primitive {@code boolean} for {@link #completed} so the JSON
 * wire format always emits a numeric/boolean value without null
 * ambiguity.</p>
 *
 * <p>{@link #progressPercentage} mirrors the value persisted on the
 * underlying progress row; {@link #watchedSeconds} /
 * {@link #totalSeconds} are surfaced separately so the front-end
 * can render a precise "12:34 / 18:00" label rather than relying
 * on the rounded percent.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #studentId} and {@link #studentName} are
 *       non-{@code null} — the teacher widget renders them
 *       unconditionally and the deep-link uses the id; every row
 *       projected from the join carries them by construction.</li>
 *   <li>{@link #studentEmail}, {@link #recordedVideoId},
 *       {@link #recordedVideoTitle}, {@link #progressPercentage},
 *       {@link #watchedSeconds}, {@link #totalSeconds} and
 *       {@link #lastWatchedAt} may be {@code null} for a freshly
 *       provisioned student who has not yet watched anything, or
 *       for a progress row whose recorded-video parent has been
 *       archived.</li>
 * </ul>
 *
 * @param studentId          {@code StudentProfile} identifier; never
 *                           {@code null}
 * @param studentName        learner display name (falls back to the
 *                           profile display name if no linked
 *                           {@code User}); never {@code null}
 * @param studentEmail       linked-user e-mail for "follow up" CTA;
 *                           {@code null} for unlinked profiles
 * @param recordedVideoId    {@code RecordedLessonVideo} identifier;
 *                           {@code null} when the parent recording
 *                           has been archived
 * @param recordedVideoTitle short title of the recording;
 *                           {@code null} when the parent recording
 *                           has been archived
 * @param progressPercentage rounded 0–100 watch-completion percent;
 *                           may be {@code null} on legacy rows
 * @param completed          {@code true} once the student crossed
 *                           the watch-completion threshold
 * @param watchedSeconds     accumulated unique seconds watched; may
 *                           be {@code null} on legacy rows
 * @param totalSeconds       full duration of the recording in
 *                           seconds; may be {@code null} when the
 *                           recording's metadata predates the
 *                           {@code duration_seconds} column
 * @param lastWatchedAt      ISO-8601 string of the most recent
 *                           heartbeat; {@code null} when the
 *                           student has never watched
 */
@JsonPropertyOrder({
        "studentId",
        "studentName",
        "studentEmail",
        "recordedVideoId",
        "recordedVideoTitle",
        "progressPercentage",
        "completed",
        "watchedSeconds",
        "totalSeconds",
        "lastWatchedAt"
})
public record LessonVideoProgressSummaryResponse(
        Long studentId,
        String studentName,
        String studentEmail,
        Long recordedVideoId,
        String recordedVideoTitle,
        Integer progressPercentage,
        boolean completed,
        Integer watchedSeconds,
        Integer totalSeconds,
        String lastWatchedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the teacher progress widget renders
     * unconditionally. All optional fields are intentionally
     * permitted to be {@code null} per the invariants documented
     * above.
     */
    public LessonVideoProgressSummaryResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
    }
}
