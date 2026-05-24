package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
 */
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
}
