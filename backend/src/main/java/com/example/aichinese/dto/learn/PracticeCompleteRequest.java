package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * A finished run of a set: the first answer to each item, in order (a quiz's "in a row" bonus counts this order),
 * or for tracing the strokes drawn. Flashcards send no answers (reviewing completes them).
 */
public record PracticeCompleteRequest(
        @Size(max = 40, message = "Too many answers.")
        List<@Valid PracticeItemAnswer> answers,

        @Min(value = 0, message = "Strokes must not be negative.")
        @Max(value = 500, message = "Too many strokes.")
        Integer strokeCount) {

    public PracticeCompleteRequest {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }
}
