package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/feedback} — a teacher
 * leaves written feedback on a student's lesson progress or quiz
 * attempt.
 *
 * <p>Persisted as
 * {@link com.example.aichinese.entity.TeacherFeedback}; surfaces to
 * the student via {@link StudentTeacherFeedbackResponse} and to the
 * teacher via {@link TeacherFeedbackResponse}. The
 * {@link #feedbackType} drives the {@code weakAreas} aggregation on
 * {@code TeacherStudentSummaryResponse} and
 * {@code StudentProgressSummaryResponse} — repeated feedback of
 * the same type marks that area as a "weak area" for the student.</p>
 *
 * <h2>{@link #lessonId} / {@link #quizAttemptId}</h2>
 * <p>Both are optional and mutually exclusive in spirit (feedback
 * targets either a lesson or a quiz attempt, not both). Either
 * may be {@code null}; passing both is permitted by validation but
 * the front-end uses only one to render the breadcrumb. Service
 * validates the calling teacher has visibility on whichever target
 * is supplied.</p>
 *
 * <h2>{@link #feedbackType}</h2>
 * <p>Free-form short tag ({@code "PRONUNCIATION"},
 * {@code "VOCABULARY"}, {@code "ENCOURAGEMENT"}, etc.) used as the
 * {@code weakAreas} key. Optional — when omitted, the feedback is
 * still recorded but does not contribute to weak-area aggregation.</p>
 *
 * <h2>{@link #message}</h2>
 * <p>Required, 5-2000 chars. The lower bound prevents trivial
 * "ok" / "nice" feedback; the cap matches the schema column. The
 * service sanitises the message before persistence.</p>
 */
public record CreateTeacherFeedbackRequest(
        @NotNull(message = "Student id is required.")
        @Positive(message = "Student id must be positive.")
        Long studentId,

        @Positive(message = "Lesson id must be positive.")
        Long lessonId,

        @Positive(message = "Quiz attempt id must be positive.")
        Long quizAttemptId,

        @Size(max = 30, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = 5, max = 2000, message = "Feedback message must be between 5 and 2000 characters.")
        String message) {
}
