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
 * Body for {@code POST /api/v1/admin/activities/tracing-exercises}
 * (create) and
 * {@code PUT /api/v1/admin/activities/tracing-exercises/{id}} (update).
 *
 * <p>Captures a single character-tracing exercise — the learner traces
 * over {@link #targetCharacter} using the SVG stroke guides at
 * {@link #worksheetUrl}. {@link #pinyin} and {@link #meaning} are
 * scaffolding fields shown alongside the practice surface so beginners
 * understand what they are tracing.</p>
 *
 * <p>{@link #worksheetUrl} is optional at request time so an admin can
 * stage the exercise metadata first and upload the worksheet asset
 * separately; the publication gate refuses to flip a tracing exercise
 * to {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * until both the worksheet URL and the target character are set. The
 * {@code @Pattern} on {@link #worksheetUrl} matches the project-wide
 * media-URL convention (see {@code RecordedLessonVideoRequest}) and
 * rejects {@code javascript:}, {@code data:}, {@code file:} and
 * protocol-relative URLs that could otherwise reach learner browsers.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary and {@link AdminFlashcardRequest} for the rationale
 * behind the tag-list cap and the {@code estimatedSeconds} upper bound.</p>
 *
 * @param title            exercise title; required; max 200 chars
 * @param worksheetUrl     optional SVG/PNG stroke-guide URL; max 255
 *                         chars; must be {@code http(s)://} or empty
 * @param targetCharacter  optional Chinese character being traced;
 *                         max 100 chars
 * @param pinyin           optional pinyin scaffold; max 255 chars
 * @param meaning          optional gloss; max 500 chars
 * @param instructions     optional learner-facing instructions; max
 *                         1000 chars
 * @param hskLevel         optional HSK band, {@code 1..6}
 * @param ageBand          optional target age cohort
 * @param difficulty       optional easy/medium/hard tier
 * @param topic            optional short topic label; max 100 chars
 * @param tags             optional list of free-form tags; up to 8
 *                         non-blank entries, each max 50 chars
 * @param xpReward         optional base XP, {@code 0..1000}
 * @param estimatedSeconds optional time-to-complete hint, {@code 0..86400}
 */
public record AdminTracingExerciseRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,

        @Pattern(
                regexp = "^(https?://.+)?$",
                message = "Worksheet URL must start with http:// or https://.")
        @Size(max = 255, message = "Worksheet URL must not exceed 255 characters.")
        String worksheetUrl,

        @Size(max = 100, message = "Target character must not exceed 100 characters.")
        String targetCharacter,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @Size(max = 500, message = "Meaning must not exceed 500 characters.")
        String meaning,

        @Size(max = 1000, message = "Instructions must not exceed 1000 characters.")
        String instructions,

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
