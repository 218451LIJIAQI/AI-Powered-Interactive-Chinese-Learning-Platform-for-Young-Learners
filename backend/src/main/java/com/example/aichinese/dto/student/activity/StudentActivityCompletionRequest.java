package com.example.aichinese.dto.student.activity;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The learner's answers for a practice activity; the server checks them before any XP is
 * awarded. Each activity type uses its own part: matching {@code matches}, drag-and-drop
 * {@code placements}, listening {@code answers} (questionIndex + answerText) or {@code responseText},
 * story {@code answers} (questionId + optionIndex), tracing {@code strokeCount}.
 */
public record StudentActivityCompletionRequest(
        @Size(max = 200, message = "Too many matches.")
        @Valid
        List<@NotNull(message = "Match must not be null.") Match> matches,

        @Size(max = 200, message = "Too many placements.")
        @Valid
        List<@NotNull(message = "Placement must not be null.") Placement> placements,

        @Size(max = 200, message = "Too many answers.")
        @Valid
        List<@NotNull(message = "Answer must not be null.") Answer> answers,

        @Min(value = 0, message = "Stroke count must not be negative.")
        @Max(value = 10000, message = "Stroke count is too large.")
        Integer strokeCount,

        @Size(max = 2000, message = "Response must be 2000 characters or fewer.")
        String responseText) {

    public StudentActivityCompletionRequest {
        // Null elements are kept so that the @NotNull element constraints report field errors.
        matches = immutableListOrEmpty(matches);
        placements = immutableListOrEmpty(placements);
        answers = immutableListOrEmpty(answers);
        responseText = strip(responseText);
    }

    /** Matching game pair chosen by the learner. */
    public record Match(
            @NotNull(message = "Left item id is required.") Long leftItemId,
            @Size(max = 500, message = "Match text must be 500 characters or fewer.") String rightText) {
    }

    /** Drag-and-drop placement chosen by the learner. */
    public record Placement(
            @NotNull(message = "Item id is required.") Long itemId,
            @Size(max = 200, message = "Zone must be 200 characters or fewer.") String zone) {
    }

    /** Listening (questionIndex + answerText) or story (questionId + optionIndex) answer. */
    public record Answer(
            Long questionId,
            @Min(value = 0, message = "Question index must not be negative.") Integer questionIndex,
            @Min(value = 0, message = "Option index must not be negative.") Integer optionIndex,
            @Size(max = 500, message = "Answer must be 500 characters or fewer.") String answerText) {
    }
}