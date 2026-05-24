package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Internal payload assembled by the multipart endpoints under
 * {@code /api/v1/teachers/lessons/{lessonId}/recorded-videos}:
 * <ul>
 *   <li>{@code POST} (multipart) — upload a new recorded video</li>
 *   <li>{@code PUT /{videoId}} (multipart) — replace metadata
 *       and/or the file</li>
 * </ul>
 *
 * <p>Note: this DTO is <em>not</em> deserialised from JSON. The
 * controller binds individual {@code @RequestParam}s and the
 * uploaded {@code MultipartFile}, then constructs an instance of
 * this record via the canonical constructor and passes it into
 * {@code RecordedLessonService}. The validation annotations apply
 * to the bound request params.</p>
 *
 * <h2>{@link #sourceType}</h2>
 * <p>Enum-string discriminator from
 * {@link com.example.aichinese.entity.enums.RecordedVideoSourceType}
 * (e.g. {@code "UPLOADED"}, {@code "EXTERNAL"}). When
 * {@code "EXTERNAL"}, the file is not stored server-side and the
 * client is expected to set {@link #externalVideoUrl} (an
 * {@code http(s)://} link). When {@code "UPLOADED"}, the multipart
 * file is persisted via {@code StoredFileService} and
 * {@link #externalVideoUrl} is ignored.</p>
 *
 * <p>{@link #durationSeconds} is optional at upload time —
 * {@code RecordedLessonService} extracts it from the file metadata
 * when omitted. {@link #sortOrder} controls the position in the
 * lesson's video list and is required (no implicit append).</p>
 *
 * <p>{@link #packageId} is captured but ignored when the lesson is
 * already attached to a package — it is included to support the
 * future "promote to package-level video" workflow without changing
 * the wire format.</p>
 */
public record RecordedLessonVideoRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Positive(message = "Package id must be positive.")
        Long packageId,

        @NotBlank(message = "Source type is required.")
        @Size(max = 30, message = "Source type must be 30 characters or fewer.")
        String sourceType,

        @Pattern(regexp = "^(https?://.+)?$", message = "External video URL must start with http:// or https://.")
        @Size(max = 500, message = "External video URL must be 500 characters or fewer.")
        String externalVideoUrl,

        @PositiveOrZero(message = "Duration must not be negative.")
        Integer durationSeconds,

        @NotNull(message = "Display order is required.")
        @PositiveOrZero(message = "Display order must not be negative.")
        Integer sortOrder) {
}
