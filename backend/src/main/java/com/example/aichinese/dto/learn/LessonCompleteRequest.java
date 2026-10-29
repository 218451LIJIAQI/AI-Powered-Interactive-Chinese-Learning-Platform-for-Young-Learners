package com.example.aichinese.dto.learn;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Every answer of a played lesson (first tries, in order) and how long it took. */
public record LessonCompleteRequest(
        @NotNull(message = "Answers are required.")
        @Size(max = 40, message = "Too many answers.")
        List<@Valid QuestionAnswer> answers,

        @Min(value = 0, message = "Duration must not be negative.")
        @Max(value = 86_400, message = "Duration must not exceed one day.")
        Integer durationSeconds) {
}
