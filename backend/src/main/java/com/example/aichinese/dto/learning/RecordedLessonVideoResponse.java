package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *   <li>{@code "UPLOADED_FILE"} — {@link #streamUrl} is the in-app
 *       streaming URL (typically the proxied
 *       {@code GET /api/v1/students/recorded-videos/{videoId}/stream}
 *       endpoint, which serves the bytes with appropriate access
 *       checks). {@link #externalVideoUrl} is {@code null}.</li>
 *   <li>{@code "EXTERNAL_URL"} — {@link #externalVideoUrl} is the
 *       publisher-supplied {@code http(s)://} link;
 *       {@link #streamUrl} is {@code null}.</li>
 * </ul>
 *
 * <h2>{@link #active}</h2>
 * <p>Soft-delete flag — {@code false} for archived videos. The
 * student-facing list filters to {@code active = true} only; the
 * teacher-facing list includes archived rows behind a toggle.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #lessonId}, {@link #title},
 *       {@link #sortOrder}, {@link #createdAt} and {@link #updatedAt}
 *       are non-{@code null} — they correspond to entity columns
 *       declared {@code NOT NULL} (or to
 *       {@link com.example.aichinese.entity.base.BaseEntity}'s
 *       always-populated identity / timestamp fields).</li>
 *   <li>{@link #sourceType} is nominally non-null (the entity
 *       column is {@code NOT NULL}) but is permissively typed for
 *       defensive mapping of legacy or partially-migrated rows.</li>
 *   <li>{@link #packageId}, {@link #description},
 *       {@link #externalVideoUrl}, {@link #streamUrl} and
 *       {@link #durationSeconds} may be {@code null} per the
 *       source-type XOR rule and optional metadata semantics.</li>
 * </ul>
 *
 * @param id               {@link com.example.aichinese.entity.RecordedLessonVideo}
 *                         identifier; never {@code null}
 * @param lessonId         parent {@code Lesson} id; never
 *                         {@code null}
 * @param packageId        optional package id (denormalised); may
 *                         be {@code null}
 * @param title            short video title; never {@code null}
 * @param description      optional long-form description; may be
 *                         {@code null}
 * @param sourceType       enum-name string from
 *                         {@link com.example.aichinese.entity.enums.RecordedVideoSourceType};
 *                         nominally non-null
 * @param externalVideoUrl publisher-supplied URL when
 *                         {@link #sourceType} is
 *                         {@code "EXTERNAL_URL"}, else {@code null}
 * @param streamUrl        in-app streaming URL when
 *                         {@link #sourceType} is
 *                         {@code "UPLOADED_FILE"}, else {@code null}
 * @param durationSeconds  video duration in seconds; {@code null}
 *                         when the upload's metadata was not
 *                         extractable
 * @param sortOrder        0-based render position; never
 *                         {@code null}
 * @param active           {@code false} for archived (soft-deleted)
 *                         rows
 * @param createdAt        row creation instant; never {@code null}
 * @param updatedAt        last-modification instant; never
 *                         {@code null}
 */
@JsonPropertyOrder({
        "id",
        "lessonId",
        "packageId",
        "title",
        "description",
        "sourceType",
        "externalVideoUrl",
        "streamUrl",
        "durationSeconds",
        "sortOrder",
        "active",
        "createdAt",
        "updatedAt"
})
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

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / ordering / timestamp fields the video
     * card renders unconditionally per the entity schema's
     * {@code NOT NULL} constraints.
     */
    public RecordedLessonVideoResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(sortOrder, "sortOrder must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
