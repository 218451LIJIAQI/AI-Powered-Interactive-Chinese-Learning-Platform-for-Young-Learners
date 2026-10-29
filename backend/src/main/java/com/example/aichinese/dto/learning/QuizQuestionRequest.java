package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A quiz question with its options, for adding and for updating it. */
public record QuizQuestionRequest(
        @NotBlank(message = "Question type is required.")
        @Size(max = 50, message = "Question type must be 50 characters or fewer.")
        String questionType,
        @NotBlank(message = "Question text is required.")
        @Size(max = 2000, message = "Question text must be 2000 characters or fewer.")
        String questionText,
        @Size(max = 1000, message = "Correct answer text must be 1000 characters or fewer.")
        String correctAnswerText,
        @NotNull(message = "Question points are required.")
        @Positive(message = "Question points must be greater than 0.")
        Integer points,
        @Size(max = 200, message = "A quiz question cannot contain more than 200 options.")
        @Valid
        List<@NotNull(message = "Quiz option must not be null.") QuizOptionRequest> options,
        @Size(max = 2000, message = "Explanation must be 2000 characters or fewer.")
        String explanation,

        @Size(max = 1000, message = "Hint must be 1000 characters or fewer.")
        String hint,

        /** Recording for a LISTEN_CHOOSE question (SDD 5.4). */
        @Size(max = 500, message = "Audio link must be 500 characters or fewer.")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "Audio link must start with http:// or https:// and contain no spaces.")
        String audioUrl,

        /** Picture for a MATCH_WORD_IMAGE question (SDD 5.4). */
        @Size(max = 500, message = "Image link must be 500 characters or fewer.")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "Image link must start with http:// or https:// and contain no spaces.")
        String imageUrl) {

    public QuizQuestionRequest {
        questionType = strip(questionType);
        questionText = strip(questionText);
        correctAnswerText = stripToNull(correctAnswerText);
        explanation = stripToNull(explanation);
        hint = stripToNull(hint);
        audioUrl = stripToNull(audioUrl);
        imageUrl = stripToNull(imageUrl);
        options = immutableListOrEmpty(options);
    }
}
