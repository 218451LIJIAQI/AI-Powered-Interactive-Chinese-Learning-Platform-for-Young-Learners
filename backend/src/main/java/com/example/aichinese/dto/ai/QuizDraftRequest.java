package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The AI-assisted quiz draft of the admin lesson editor: questions about {@code topic} for the quiz of the lesson. A
 * lesson quiz has exactly 5 questions, so the draft writes at most the places left in it.
 */
public record QuizDraftRequest(
        @NotNull(message = "Choose the lesson the quiz is for.")
        @Positive(message = "Lesson id must be positive.")
        Long lessonId,
        @NotBlank(message = "Topic is required.")
        @Size(max = 200, message = "Topic must not exceed 200 characters.")
        String topic,
        @Size(max = 100, message = "Level must not exceed 100 characters.")
        String level,
        @Min(value = 1, message = "Number of questions must be at least 1.")
        @Max(value = 5, message = "Number of questions must not exceed 5.")
        Integer numberOfQuestions,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference) {

    public QuizDraftRequest {
        topic = strip(topic);
        level = stripToNull(level);
        languagePreference = stripToNull(languagePreference);
    }

    @Override
    public String toString() {
        return "QuizDraftRequest[lessonId=" + lessonId
                + ", topic=" + redactLength(topic)
                + ", level=" + redactLength(level)
                + ", numberOfQuestions=" + numberOfQuestions
                + ", languagePreference=" + languagePreference + "]";
    }
}
