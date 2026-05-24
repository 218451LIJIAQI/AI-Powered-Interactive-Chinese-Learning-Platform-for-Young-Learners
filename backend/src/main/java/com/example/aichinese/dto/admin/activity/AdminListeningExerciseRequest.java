package com.example.aichinese.dto.admin.activity;

import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/activities/listening-exercises}
 * (create) and
 * {@code PUT /api/v1/admin/activities/listening-exercises/{id}} (update).
 *
 * <h2>{@link #questionPayload}</h2>
 * <p>Free-form JSON document encoding the comprehension prompts and
 * acceptable answers. Held in a single string column rather than a
 * child entity table because the payload is small (typically 3-5
 * multiple-choice prompts), is treated as an opaque blob by Hibernate,
 * and the front-end is the canonical schema owner. The
 * {@code ListeningExercise.questionPayload} column is {@code LONGTEXT}
 * (≈4&nbsp;GB capacity), so the 200&nbsp;000-char cap is a defensive
 * bound that comfortably fits any realistic prompt set. The expected
 * shape is stable enough that breaking changes are versioned in
 * front-end code, not by introducing new entity columns.</p>
 *
 * <h2>{@link #audioUrl}</h2>
 * <p>Optional at request time but typically required for a publishable
 * exercise — the service-layer publication gate refuses to flip status
 * to {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * when no audio asset is attached, since a listening exercise without
 * audio is unusable. The 255-char cap aligns with the
 * {@code listening_exercises.audio_url} column. The {@code @Pattern}
 * forbids {@code javascript:}, {@code data:}, {@code file:} and
 * protocol-relative URLs.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract this DTO conforms to and {@link AdminFlashcardRequest}
 * for the rationale behind the tag-list cap and the
 * {@code estimatedSeconds} upper bound.</p>
 *
 * @param title            exercise title; required; max 200 chars
 * @param audioUrl         optional audio URL; max 255 chars; must be
 *                         {@code http(s)://} or empty
 * @param questionPayload  opaque JSON describing prompts and answers;
 *                         max 200&nbsp;000 chars
 * @param hskLevel         optional HSK band, {@code 1..6}
 * @param ageBand          optional target age cohort
 * @param difficulty       optional easy/medium/hard tier
 * @param topic            optional short topic label; max 100 chars
 * @param tags             optional list of free-form tags; up to 8
 *                         non-blank entries, each max 50 chars
 * @param xpReward         optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint, {@code 0..86400}
 */
public record AdminListeningExerciseRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Audio URL must start with http:// or https://.")
        @Size(max = 255, message = "Audio URL must not exceed 255 characters.")
        String audioUrl,

        @Size(max = 200_000, message = "Question payload is too large.")
        String questionPayload,

        @Min(value = 1, message = "HSK level must be between 1 and 6.")
        @Max(value = 6, message = "HSK level must be between 1 and 6.")
        Integer hskLevel,

        AgeBand ageBand,

        ActivityDifficulty difficulty,

        @Size(max = 100, message = "Topic must not exceed 100 characters.")
        String topic,

        @Size(max = 8, message = "At most 8 tags are allowed.")
        List<
                @NotBlank(message = "Tag must not be blank.")
                @Size(max = 50, message = "Each tag must not exceed 50 characters.")
                String> tags,

        @Min(value = 0, message = "XP reward must be non-negative.")
        @Max(value = 1000, message = "XP reward must not exceed 1000.")
        Integer xpReward,

        @Min(value = 0, message = "Estimated seconds must be non-negative.")
        @Max(value = 86_400, message = "Estimated seconds must not exceed 86400 (24 hours).")
        Integer estimatedSeconds) {
}
