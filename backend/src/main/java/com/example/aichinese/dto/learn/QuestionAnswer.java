package com.example.aichinese.dto.learn;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One answer to a Learn question or a waiting mistake: the chosen option text, or the tiles in order separated by
 * spaces. A null answer means the question was skipped.
 */
public record QuestionAnswer(
        @NotNull(message = "Question is required.")
        Long questionId,

        @Size(max = 300, message = "Answer must not exceed 300 characters.")
        String answer) {
}
