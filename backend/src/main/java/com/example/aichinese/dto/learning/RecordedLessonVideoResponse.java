package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * Detail payload for one recorded lesson video, returned by:
 * <ul>
 *   <li>{@code GET} list and detail endpoints under
 *       {@code /api/v1/students/lessons/{lessonId}/recorded-videos}
 *       and the teacher mirror at
 *       {@code /api/v1/teachers/lessons/{lessonId}/recorded-videos}</li>
 *   <li>The response side of the multipart {@code POST}/{@code PUT}
 *       endpoints and {@code PATCH .../archive}</li>
 * </ul>
 *
 * <p>Mirrors {@link RecordedLessonVideoRequest} field-for-field plus
 * the server-computed {@link #streamUrl} and audit fields.</p>
 *
 * <h2>{@link #streamUrl} vs {@link #externalVideoUrl}</h2>
 * <p>Exactly one is populated at any given time, controlled by
 * {@link #sourceType}:</p>
 * <ul>
 *   <li>{@code "UPLOADED"} — {@link #streamUrl} is the in-app
 *       streaming URL (typically the proxied
 *       {@code GET /api/v1/students/recorded-videos/{videoId}/stream}
 *       endpoint, which serves the bytes with appropriate access
 *       checks). {@link #externalVideoUrl} is {@code null}.</li>
 *   <li>{@code "EXTERNAL"} — {@link #externalVideoUrl} is the
 *       publisher-supplied {@code http(s)://} link;
 *       {@link #streamUrl} is {@code null}.</li>
 * </ul>
 *
 * <h2>{@link #active}</h2>
 * <p>Soft-delete flag — {@code false} for archived videos. The
 * student-facing list filters to {@code active = true} only; the
 * teacher-facing list includes archived rows behind a toggle.</p>
 */
public record RecordedLessonVideoResponse(
        Long id,
        Long lessonId,
        Long packageId,
        String title,
        String description,
        String sourceType,
        String externalVideoUrl,
        String streamUrl,
        Integer durationSeconds,
        Integer sortOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
