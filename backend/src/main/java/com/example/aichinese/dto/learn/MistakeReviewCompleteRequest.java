package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** The answers of a finished review; {@code questionId} is the mistake id. */
public record MistakeReviewCompleteRequest(
        @NotEmpty(message = "Answer at least one mistake before you finish the review.")
        @Size(max = 50, message = "Too many answers.")
        List<@Valid QuestionAnswer> answers) {
}
