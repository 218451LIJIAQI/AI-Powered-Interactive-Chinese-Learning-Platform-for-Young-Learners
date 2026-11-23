package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Every answer of a played lesson (first tries, in order). */
public record LessonCompleteRequest(
        @NotNull(message = "Answers are required.")
        @Size(max = 40, message = "Too many answers.")
        List<@NotNull(message = "Question is required.") @Valid QuestionAnswer> answers) {

    public LessonCompleteRequest {
        answers = immutableListOrNull(answers);
    }
}
