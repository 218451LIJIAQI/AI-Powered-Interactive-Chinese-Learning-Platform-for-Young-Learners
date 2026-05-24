package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/feedback} — a teacher
 * authors written feedback addressed to one of their students,
 * optionally tying it to a specific lesson and / or quiz attempt.
 *
 * <p>Persisted as
 * {@link com.example.aichinese.entity.TeacherFeedback}; surfaced to
 * the receiving student via {@link StudentTeacherFeedbackResponse}
 * (read by the student-side feedback controller) and back to the
 * authoring teacher via {@link TeacherFeedbackResponse}.
 * {@link #feedbackType} maps to the
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
 * enum which is persisted as a string column and drives downstream
 * notifications, analytics aggregation and AI summarisation. The
 * companion edit DTO is {@link UpdateTeacherFeedbackRequest}, which
 * intentionally reuses {@link #MAX_FEEDBACK_TYPE_LENGTH},
 * {@link #MIN_MESSAGE_LENGTH} and {@link #MAX_MESSAGE_LENGTH} so the
 * two endpoints stay in lockstep.</p>
 *
 * <h2>Authorisation contract</h2>
 * <p>Validation here only enforces shape; ownership is enforced
 * server-side by
 * {@link com.example.aichinese.service.TeacherFeedbackService} which
 * (a) requires the calling user to be an approved teacher, (b)
 * resolves {@link #lessonId} / {@link #quizAttemptId} via the
 * teacher-scoped repository methods (so a teacher cannot reference
 * another teacher's resources), and (c) confirms the
 * teacher-student touch-point exists via
 * {@code validateRelationship}. An approved teacher cannot file
 * unsolicited feedback on arbitrary students — see
 * {@code TeacherFeedbackController} Javadoc.</p>
 *
 * <h2>{@link #lessonId} / {@link #quizAttemptId}</h2>
 * <p>Both are optional. They are <em>not</em> mutually exclusive at
 * the validation layer: a teacher may legitimately tie feedback to
 * a quiz attempt that lives under a lesson and pass both ids; the
 * service stitches them coherently. When only
 * {@link #quizAttemptId} is supplied the service back-fills the
 * lesson from {@code quizAttempt.quiz.lesson} so the persisted row
 * always carries the canonical lesson reference. When neither id is
 * supplied the feedback is recorded as a general teacher comment
 * scoped to the (teacher, student) pair.</p>
 *
 * <h2>{@link #feedbackType}</h2>
 * <p>Optional. Must, when supplied, parse to a member of
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
 * (case-insensitive; surrounding whitespace tolerated): one of
 * {@code GENERAL}, {@code QUIZ}, {@code LESSON}, {@code ENCOURAGING},
 * {@code CORRECTIVE}, {@code PROGRESS}, or {@code WEAK_AREA}.
 * Unrecognised values are rejected with {@code 400 Bad Request} at
 * the service layer. {@code null} or blank input falls back to
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType#GENERAL}.</p>
 *
 * <h2>{@link #message}</h2>
 * <p>Required, between {@link #MIN_MESSAGE_LENGTH} and
 * {@link #MAX_MESSAGE_LENGTH} characters. The lower bound prevents
 * trivial "ok" / "nice" feedback; the upper bound matches the
 * {@code MAX_FEEDBACK_MESSAGE_LENGTH} cap inside
 * {@link com.example.aichinese.service.TeacherFeedbackService}. The
 * service sanitises and content-safety-checks the message via
 * {@code ContentSafetyService.requireSafeText} before persistence,
 * which strips control characters / chat-template sentinels and
 * rejects child-unsafe content with {@code 400}.</p>
 *
 * <h2>Invariants enforced by Bean Validation</h2>
 * <ul>
 *   <li>{@link #studentId} is non-{@code null} and strictly
 *       positive.</li>
 *   <li>{@link #lessonId} / {@link #quizAttemptId} when supplied are
 *       strictly positive; either or both may be {@code null}.</li>
 *   <li>{@link #feedbackType} when supplied is at most
 *       {@link #MAX_FEEDBACK_TYPE_LENGTH} chars (additional
 *       enum-membership check happens server-side).</li>
 *   <li>{@link #message} is non-blank and between
 *       {@link #MIN_MESSAGE_LENGTH} and {@link #MAX_MESSAGE_LENGTH}
 *       characters.</li>
 * </ul>
 *
 * @param studentId      primary key of the
 *                       {@link com.example.aichinese.entity.StudentProfile}
 *                       being addressed; non-{@code null}, positive
 * @param lessonId       optional primary key of a
 *                       {@link com.example.aichinese.entity.Lesson}
 *                       owned by the authoring teacher; when
 *                       supplied must be positive
 * @param quizAttemptId  optional primary key of a
 *                       {@link com.example.aichinese.entity.QuizAttempt}
 *                       on a quiz owned by the authoring teacher;
 *                       when supplied must be positive
 * @param feedbackType   optional category tag mapped to
 *                       {@link com.example.aichinese.entity.enums.TeacherFeedbackType};
 *                       case-insensitive; {@code null} / blank
 *                       defaults to {@code GENERAL}
 * @param message        the teacher's written feedback; non-blank,
 *                       between {@link #MIN_MESSAGE_LENGTH} and
 *                       {@link #MAX_MESSAGE_LENGTH} characters;
 *                       sanitised server-side
 */
public record CreateTeacherFeedbackRequest(
        @NotNull(message = "Student id is required.")
        @Positive(message = "Student id must be positive.")
        Long studentId,

        @Positive(message = "Lesson id must be positive.")
        Long lessonId,

        @Positive(message = "Quiz attempt id must be positive.")
        Long quizAttemptId,

        @Size(max = MAX_FEEDBACK_TYPE_LENGTH, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = MIN_MESSAGE_LENGTH, max = MAX_MESSAGE_LENGTH,
                message = "Feedback message must be between 5 and 2000 characters.")
        String message) {

    /**
     * Maximum number of characters accepted in {@link #feedbackType}.
     * Tighter than the underlying {@code teacher_feedback.feedback_type}
     * column (length 50) so the API rejects payloads that could not be
     * a valid {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
     * member name long before they reach the database.
     */
    public static final int MAX_FEEDBACK_TYPE_LENGTH = 30;

    /**
     * Minimum number of characters accepted in {@link #message}.
     * Prevents trivial "ok" / "nice" feedback that would dilute the
     * pedagogical signal teachers are expected to provide.
     */
    public static final int MIN_MESSAGE_LENGTH = 5;

    /**
     * Maximum number of characters accepted in {@link #message}.
     * Mirrors the {@code MAX_FEEDBACK_MESSAGE_LENGTH} cap inside
     * {@link com.example.aichinese.service.TeacherFeedbackService}
     * which is enforced again by
     * {@code ContentSafetyService.requireSafeText} before persistence.
     */
    public static final int MAX_MESSAGE_LENGTH = 2000;
}
