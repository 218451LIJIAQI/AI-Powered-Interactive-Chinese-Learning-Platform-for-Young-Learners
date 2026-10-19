package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** The teacher-authored hint of one quiz question, revealed only on request (D8). */
@JsonPropertyOrder({ "questionId", "hint" })
public record QuizHintResponse(Long questionId, String hint) {

    public QuizHintResponse {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(hint, "hint must not be null");
    }
}
