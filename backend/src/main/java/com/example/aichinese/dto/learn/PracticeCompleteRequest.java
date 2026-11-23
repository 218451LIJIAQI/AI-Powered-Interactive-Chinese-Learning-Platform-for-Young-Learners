package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A finished run of a set: the first answer to each item, or for tracing the strokes drawn. A flashcard's answer is
 * its rating, {@code GOT_IT} or {@code STILL_LEARNING}.
 */
public record PracticeCompleteRequest(
        @Size(max = 40, message = "Too many answers.")
        List<@NotNull(message = "Item is required.") @Valid PracticeItemAnswer> answers,

        @Min(value = 0, message = "Strokes must not be negative.")
        @Max(value = 5000, message = "Too many strokes.")
        Integer strokeCount) {

    public PracticeCompleteRequest {
        answers = immutableListOrEmpty(answers);
    }
}
