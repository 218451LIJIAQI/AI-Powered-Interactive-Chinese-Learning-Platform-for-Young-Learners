package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-student video-watch progress row, returned by
 * {@code GET /api/v1/students/video-progress/{videoId}} and as the
 * response side of {@code POST}/{@code PUT}/{@code POST .../complete}
 * on the same path.
 *
 * <p>Drives the resume-from-where-you-left-off behaviour in the
 * lesson player: the front-end reads {@link #watchedSeconds} and
 * seeks the video to that timestamp on load, then starts firing
 * {@link VideoProgressRequest} heartbeats.</p>
 *
 * <p>{@link #completed} is primitive {@code boolean} (the request
 * uses boxed {@link Boolean} for the "no change" sentinel — see
 * {@link VideoProgressRequest}) so the JSON wire format always
 * emits a value. The service flips it to {@code true} when
 * {@link #progressPercentage} reaches 100 or when the explicit
 * {@code /complete} endpoint is called.</p>
 *
 * <p>{@link #lastWatchedAt} is the most recent heartbeat time and
 * powers the "Continue where you left off" widget;
 * {@link #updatedAt} carries the underlying
 * {@code BaseEntity.updatedAt} which is identical in practice but
 * preserved for consistency with other audit-bearing DTOs.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #lessonId} and {@link #updatedAt} are
 *       non-{@code null} per the entity schema's {@code NOT NULL}
 *       constraints.</li>
 *   <li>{@link #recordedVideoId} may be {@code null} when the
 *       parent recording has been archived (the progress row
 *       survives so the student's history is preserved).</li>
 *   <li>{@link #watchedSeconds}, {@link #totalSeconds} and
 *       {@link #progressPercentage} may be {@code null} on legacy
 *       rows authored before those columns were always-populated.</li>
 *   <li>{@link #lastWatchedAt} is {@code null} until the first
 *       heartbeat lands.</li>
 * </ul>
 *
 * @param id                 {@link com.example.aichinese.entity.VideoProgress}
 *                           identifier; never {@code null}
 * @param lessonId           parent {@code Lesson} identifier; never
 *                           {@code null}
 * @param recordedVideoId    parent recording id; {@code null} for
 *                           archived recordings
 * @param watchedSeconds     accumulated unique seconds watched; may
 *                           be {@code null} on legacy rows
 * @param totalSeconds       full duration in seconds; may be
 *                           {@code null} on legacy rows
 * @param progressPercentage rounded 0–100 percent; may be
 *                           {@code null} on legacy rows
 * @param completed          {@code true} once the threshold is
 *                           crossed
 * @param lastWatchedAt      most recent heartbeat instant; may be
 *                           {@code null} when never watched
 * @param updatedAt          row update instant; never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "lessonId",
        "recordedVideoId",
        "watchedSeconds",
        "totalSeconds",
        "progressPercentage",
        "completed",
        "lastWatchedAt",
        "updatedAt"
})
public record VideoProgressResponse(
        Long id,
        Long lessonId,
        Long recordedVideoId,
        Integer watchedSeconds,
        Integer totalSeconds,
        Integer progressPercentage,
        boolean completed,
        LocalDateTime lastWatchedAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / timestamp fields per the entity schema's
     * {@code NOT NULL} constraints. All optional / legacy-tolerant
     * fields are intentionally permitted to be {@code null} per
     * the invariants documented above.
     */
    public VideoProgressResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
