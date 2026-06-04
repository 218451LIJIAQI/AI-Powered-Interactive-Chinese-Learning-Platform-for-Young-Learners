package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "questionType",
        "questionText",
        "correctAnswerText",
        "points",
        "displayOrder",
        "explanation",
        "options"
})
public record TeacherQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        String correctAnswerText,
        Integer points,
        Integer displayOrder,
        String explanation,
        List<TeacherQuizOptionResponse> options) {

    public TeacherQuizQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
