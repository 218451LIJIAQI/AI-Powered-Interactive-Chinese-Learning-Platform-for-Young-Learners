package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A quiz question of a class lesson with its options, for adding and for updating it (admin lesson editor). */
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

        /** Recording for a LISTEN_CHOOSE question (SDD 5.4); scheme/host validated in LessonQuizEditor when required. */
        @Size(max = 500, message = "Audio link must be 500 characters or fewer.")
        String audioUrl,

        /** Picture for a MATCH_WORD_IMAGE question (SDD 5.4); scheme/host validated in LessonQuizEditor when required. */
        @Size(max = 500, message = "Image link must be 500 characters or fewer.")
        String imageUrl,

        /** Where a new question goes in its quiz (1 = first); none: last. Ignored when a question is updated. */
        @Positive(message = "Position must be 1 or more.")
        Integer position,

        /**
         * True for a new question taken from the AI quiz draft: it stays an unchecked AI draft until an admin keeps or
         * edits it. Ignored when a question is updated (an edit checks it).
         */
        Boolean aiDraft) {

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
