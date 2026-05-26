package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One comprehension prompt attached to an admin-managed story
 * activity.
 *
 * <p>Replayed as part of the parent {@link AdminStoryActivityRequest}
 * so the whole story (body + prompts) can be edited atomically.
 * {@code AdminActivityService.updateStoryActivity} deletes the entire
 * existing children set and re-inserts the incoming list inside the
 * same JPA transaction, so a partial-failure state where the body
 * persists while a question is rejected is impossible.</p>
 *
 * <h2>{@link #optionsPayload}</h2>
 * <p>JSON document encoding the multiple-choice options and which one
 * is correct. Persisted inline as {@code TEXT} on the
 * {@code story_questions} table rather than as separate option rows
 * because story prompts are intentionally short (typically 3-4
 * options each) — a single column avoids the join cost in the
 * student practice loop.</p>
 *
 * <p>The {@code @Size} cap of 32&nbsp;000 chars is deliberately well
 * below the MySQL {@code TEXT} column's 65&nbsp;535-byte limit:
 * {@code utf8mb4} reserves up to four bytes per character, so the
 * conservative char cap guarantees the value always fits even when
 * the JSON contains four-byte supplementary code points (emoji,
 * historic Han characters). The previous 65&nbsp;535-char cap could
 * silently overflow the column for multi-byte content. The expected
 * JSON shape is owned by the front-end; the backend treats the
 * payload as an opaque non-blank string.</p>
 *
 * <p>{@link #explanation} is shown to the learner after they submit
 * an answer (regardless of correctness) so they can learn from the
 * prompt even when they got it right.</p>
 *
 * <p>{@link #sortOrder} preserves the author's canonical question
 * order; the front-end may optionally shuffle on each play. The upper
 * {@code @Max} bound is a defensive cap, not a business rule.</p>
 *
 * @param questionText   prompt text shown to the learner; required,
 *                       max 500 chars
 *                       ({@code story_questions.question_text VARCHAR(500)})
 * @param optionsPayload opaque JSON describing the option list;
 *                       required, max 32&nbsp;000 chars
 *                       ({@code story_questions.options_payload TEXT})
 * @param explanation    optional rationale shown after submission;
 *                       max 1000 chars
 *                       ({@code story_questions.explanation VARCHAR(1000)})
 * @param sortOrder      authoring order, {@code 0..10000}; nullable
 *                       (mapper falls back to {@code 0})
 */
public record AdminStoryQuestionRequest(
        @NotBlank(message = "Question text is required.")
        @Size(max = 500, message = "Question text must not exceed 500 characters.")
        String questionText,

        @NotBlank(message = "Options payload (JSON) is required.")
        @Size(max = 32_000, message = "Options payload is too large.")
        String optionsPayload,

        @Size(max = 1000, message = "Explanation must not exceed 1000 characters.")
        String explanation,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {

    public AdminStoryQuestionRequest {
        questionText = strip(questionText);
        optionsPayload = strip(optionsPayload);
        explanation = stripToNull(explanation);
    }
}
