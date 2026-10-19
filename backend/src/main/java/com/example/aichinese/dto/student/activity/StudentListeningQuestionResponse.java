package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

/** One listening question without its answer; empty {@code options} means "type the answer". */
public record StudentListeningQuestionResponse(int index, String questionText, List<String> options) {

    public StudentListeningQuestionResponse {
        Objects.requireNonNull(questionText, "questionText must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}