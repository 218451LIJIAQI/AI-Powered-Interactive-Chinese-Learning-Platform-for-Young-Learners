package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/feedback/{id}} — a teacher
 * edits one of their previously-submitted feedback rows.
 *
 * <p>Strictly leaner than {@link CreateTeacherFeedbackRequest}: the
 * target tuple ({@code studentId} / {@code lessonId} /
 * {@code quizAttemptId}) is fixed at create time and cannot be
 * mutated, so only the editorial fields ({@link #feedbackType} and
 * {@link #message}) are accepted here.
 * {@link com.example.aichinese.service.TeacherFeedbackService#updateFeedback}
 * enforces that the caller is the original feedback author by
 * resolving the row through
 * {@code teacherFeedbackRepository.findByIdAndTeacherUserId}, and
 * runs the new message through
 * {@code ContentSafetyService.requireSafeText} just like the create
 * path. The edit is also audit-logged so admins reviewing feedback
 * have a record of changes.</p>
 *
 * <h2>Field-level caps</h2>
 * <p>The validation constants ({@link #MAX_FEEDBACK_TYPE_LENGTH},
 * {@link #MIN_MESSAGE_LENGTH}, {@link #MAX_MESSAGE_LENGTH}) are
 * delegated to {@link CreateTeacherFeedbackRequest} so the create
 * and update endpoints accept exactly the same shape and consumers
 * can pin a single set of constants. Any future cap change therefore
 * propagates here automatically.</p>
 *
 * <h2>{@link #feedbackType}</h2>
 * <p>Optional. When supplied must parse to a member of
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType}
 * (case-insensitive); unrecognised values are rejected with
 * {@code 400 Bad Request} at the service layer. {@code null} or
 * blank input falls back to
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType#GENERAL}.
 * See {@link CreateTeacherFeedbackRequest} for the full enum
 * value list.</p>
 *
 * <h2>{@link #message}</h2>
 * <p>Required; between
 * {@value CreateTeacherFeedbackRequest#MIN_MESSAGE_LENGTH} and
 * {@value CreateTeacherFeedbackRequest#MAX_MESSAGE_LENGTH}
 * characters. Sanitised and content-safety-checked server-side
 * before persistence.</p>
 *
 * <h2>Invariants enforced by Bean Validation</h2>
 * <ul>
 *   <li>{@link #feedbackType} when supplied is at most
 *       {@link #MAX_FEEDBACK_TYPE_LENGTH} chars.</li>
 *   <li>{@link #message} is non-blank and between
 *       {@link #MIN_MESSAGE_LENGTH} and {@link #MAX_MESSAGE_LENGTH}
 *       characters.</li>
 * </ul>
 *
 * @param feedbackType optional category tag mapped to
 *                     {@link com.example.aichinese.entity.enums.TeacherFeedbackType};
 *                     case-insensitive; {@code null} / blank
 *                     defaults to {@code GENERAL}
 * @param message      the teacher's revised feedback; non-blank,
 *                     between {@link #MIN_MESSAGE_LENGTH} and
 *                     {@link #MAX_MESSAGE_LENGTH} characters;
 *                     sanitised server-side
 */
public record UpdateTeacherFeedbackRequest(
        @Size(max = MAX_FEEDBACK_TYPE_LENGTH, message = "Feedback type must be 30 characters or fewer.")
        String feedbackType,

        @NotBlank(message = "Feedback message is required.")
        @Size(min = MIN_MESSAGE_LENGTH, max = MAX_MESSAGE_LENGTH,
                message = "Feedback message must be between 5 and 2000 characters.")
        String message) {

    /**
     * Maximum {@link #feedbackType} length; aliased to
     * {@link CreateTeacherFeedbackRequest#MAX_FEEDBACK_TYPE_LENGTH}
     * so the create and update endpoints stay in lockstep.
     */
    public static final int MAX_FEEDBACK_TYPE_LENGTH =
            CreateTeacherFeedbackRequest.MAX_FEEDBACK_TYPE_LENGTH;

    /**
     * Minimum {@link #message} length; aliased to
     * {@link CreateTeacherFeedbackRequest#MIN_MESSAGE_LENGTH}.
     */
    public static final int MIN_MESSAGE_LENGTH =
            CreateTeacherFeedbackRequest.MIN_MESSAGE_LENGTH;

    /**
     * Maximum {@link #message} length; aliased to
     * {@link CreateTeacherFeedbackRequest#MAX_MESSAGE_LENGTH}.
     */
    public static final int MAX_MESSAGE_LENGTH =
            CreateTeacherFeedbackRequest.MAX_MESSAGE_LENGTH;
}
