package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * (currently {@code "UPLOADED_FILE"} or {@code "EXTERNAL_URL"}).
 * When {@code "EXTERNAL_URL"}, the file is not stored server-side
 * and the client is expected to set {@link #externalVideoUrl} (an
 * {@code http(s)://} link). When {@code "UPLOADED_FILE"}, the
 * multipart file is persisted via {@code StoredFileService} and
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
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * every string field so Bean Validation sees the canonical form
 * (a paste with stray whitespace cannot inflate the length past
 * the cap, sneak past the {@code http(s)://} regex, or fail enum
 * parsing) and so every downstream consumer sees the same trimmed
 * value. {@link #description} and {@link #externalVideoUrl}
 * collapse to {@code null} when blank so the service's
 * "is this set?" check is a single null-check.</p>
 *
 * @param title            short human-readable video title
 *                         (1–200 chars after trim); required
 * @param description      optional long-form description (max 1000
 *                         chars after trim); {@code null} or blank
 *                         collapses to {@code null}
 * @param packageId        optional denormalised
 *                         {@link com.example.aichinese.entity.CoursePackage}
 *                         id (ignored when the lesson is already
 *                         package-attached)
 * @param sourceType       enum-name string from
 *                         {@link com.example.aichinese.entity.enums.RecordedVideoSourceType};
 *                         required, parsed server-side
 * @param externalVideoUrl {@code http(s)://} link when
 *                         {@link #sourceType} is
 *                         {@code "EXTERNAL_URL"}; max 500 chars; no
 *                         embedded whitespace; {@code null} or
 *                         blank collapses to {@code null}
 * @param durationSeconds  optional video duration; non-negative
 *                         when present; the service extracts it
 *                         from file metadata when omitted
 * @param sortOrder        0-based render position in the lesson's
 *                         video list; required, non-negative
 */
@JsonPropertyOrder({
        "title",
        "description",
        "packageId",
        "sourceType",
        "externalVideoUrl",
        "durationSeconds",
        "sortOrder"
})
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

        @Pattern(regexp = "^(https?://\\S+)?$", message = "External video URL must start with http:// or https:// and contain no whitespace.")
        @Size(max = 500, message = "External video URL must be 500 characters or fewer.")
        String externalVideoUrl,

        @PositiveOrZero(message = "Duration must not be negative.")
        Integer durationSeconds,

        @NotNull(message = "Display order is required.")
        @PositiveOrZero(message = "Display order must not be negative.")
        Integer sortOrder) {

    /**
     * Defensive canonical constructor: trims every string field so
     * Bean Validation sees the canonical form (preventing length-cap
     * bypass via leading/trailing whitespace and ensuring the regex
     * evaluates against the canonical form) and collapses optional
     * blank-only strings to {@code null} so downstream "is this set?"
     * checks are a single null-check.
     */
    public RecordedLessonVideoRequest {
        title = trimOrNull(title);
        description = trimToNullIfBlank(description);
        sourceType = trimOrNull(sourceType);
        externalVideoUrl = trimToNullIfBlank(externalVideoUrl);
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
