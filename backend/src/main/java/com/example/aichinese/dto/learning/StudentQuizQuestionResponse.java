package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Quiz question for taking: no answer key and no hint text; {@code hintAvailable} says a hint can be requested (D8). */
@JsonPropertyOrder({ "id", "questionType", "questionText", "points", "displayOrder", "options", "hintAvailable" })
public record StudentQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        Integer points,
        Integer displayOrder,
        List<QuizOptionResponse> options,
        boolean hintAvailable) {

    public StudentQuizQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
