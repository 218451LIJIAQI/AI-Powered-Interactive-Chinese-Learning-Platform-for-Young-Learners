package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

/** Story comprehension question without explanation or correct flag (FR-LRN-13). */
public record StudentStoryQuestionResponse(
        Long id,
        String questionText,
        List<StudentStoryOptionResponse> options,
        Integer sortOrder) {

    public StudentStoryQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}