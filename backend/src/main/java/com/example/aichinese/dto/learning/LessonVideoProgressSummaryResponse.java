package com.example.aichinese.dto.learning;

/**
 * One row of the teacher-facing video-watch report; surfaced by the
 * teacher dashboard's "Who is watching what" widget.
 *
 * <p>Joins per-student metadata
 * ({@link #studentId}, {@link #studentName}, {@link #studentEmail},
 * denormalised from {@code users}) with the corresponding
 * {@link com.example.aichinese.entity.LessonVideoProgress} row so
 * the teacher can see at a glance who has finished which recording
 * — and follow up directly via the email link if needed.</p>
 *
 * <p>{@link #lastWatchedAt} is intentionally typed as {@code String}
 * (not {@code LocalDateTime}) so the service can format it for
 * "X minutes ago" / locale-specific display before serialisation;
 * this DTO carries whatever the service decides to render. Empty
 * string means "never watched". Primitive {@code boolean} for
 * {@link #completed}.</p>
 *
 * <p>{@link #progressPercentage} mirrors the value persisted on the
 * underlying {@code LessonVideoProgress} row;
 * {@link #watchedSeconds} / {@link #totalSeconds} are surfaced
 * separately so the front-end can render a precise "12:34 / 18:00"
 * label rather than relying on the rounded percent.</p>
 */
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
}
