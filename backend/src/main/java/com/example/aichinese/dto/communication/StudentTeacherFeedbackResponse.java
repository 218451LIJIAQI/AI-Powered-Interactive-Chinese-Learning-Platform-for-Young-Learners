package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Student-facing view of one teacher feedback row returned by the
 * student-side feedback endpoints under
 * {@code /api/v1/students/feedback}:
 * <ul>
 *   <li>{@code GET /api/v1/students/feedback} — paginated inbox
 *       of the student's received feedback, newest first.</li>
 *   <li>{@code GET /api/v1/students/feedback/{id}} — single-row
 *       lookup for deep-linking from the inbox card.</li>
 * </ul>
 *
 * <p>Drives the student's "Feedback from teachers" inbox.
 * Compared to the teacher-facing {@link TeacherFeedbackResponse}:</p>
 * <ul>
 *   <li>Drops {@code teacherId} / {@code studentId} /
 *       {@code studentName} — the student does not need to
 *       navigate to the teacher's admin profile, and the student
 *       is the caller so their own identity is implicit.</li>
 *   <li>Keeps {@link #teacherName} (denormalised from
 *       {@code users.display_name}) so the inbox renders as
 *       "Feedback from Dr. Chen" without a follow-up lookup.</li>
 *   <li>Keeps both lesson and quiz-attempt linkage so the student
 *       can deep-link from the feedback card back to the referenced
 *       lesson or attempt review.</li>
 * </ul>
 *
 * <p>Students cannot edit or delete teacher feedback — the inbox is
 * read-only from the student's perspective. See
 * {@link CreateTeacherFeedbackRequest} for the teacher's authoring
 * side and {@link UpdateTeacherFeedbackRequest} for the teacher's
 * edit side.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #message}, {@link #createdAt} and
 *       {@link #updatedAt} are always non-{@code null} — they map
 *       to the primary key, the {@code NOT NULL} feedback message
 *       column, and the
 *       {@link com.example.aichinese.entity.base.BaseEntity}
 *       managed timestamps.</li>
 *   <li>{@link #teacherName} may be {@code null} when the authoring
 *       teacher account has since been deleted or has no display
 *       name set.</li>
 *   <li>{@link #lessonId} / {@link #lessonTitle} are {@code null}
 *       when no lesson was attached at authoring time; similarly
 *       {@link #quizAttemptId} / {@link #quizTitle} are {@code null}
 *       when no quiz attempt was attached.</li>
 *   <li>{@link #feedbackType} may be {@code null} for legacy rows
 *       authored before the category tag was added; otherwise it is
 *       the {@code .name()} of a member of
 *       {@link com.example.aichinese.entity.enums.TeacherFeedbackType}.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param id             primary key of the underlying
 *                       {@link com.example.aichinese.entity.TeacherFeedback};
 *                       never {@code null}
 * @param teacherName    display name of the authoring teacher;
 *                       may be {@code null}
 * @param lessonId       primary key of the linked
 *                       {@link com.example.aichinese.entity.Lesson};
 *                       {@code null} when no lesson was attached
 * @param lessonTitle    title of the linked lesson;
 *                       {@code null} when no lesson was attached
 * @param quizAttemptId  primary key of the linked
 *                       {@link com.example.aichinese.entity.QuizAttempt};
 *                       {@code null} when no quiz attempt was
 *                       attached
 * @param quizTitle      title of the linked quiz;
 *                       {@code null} when no quiz attempt was
 *                       attached
 * @param feedbackType   {@code .name()} of
 *                       {@link com.example.aichinese.entity.enums.TeacherFeedbackType};
 *                       {@code null} on legacy rows
 * @param message        the teacher's written feedback, plain text;
 *                       never {@code null}
 * @param createdAt      timestamp the feedback was first authored;
 *                       never {@code null}
 * @param updatedAt      timestamp the feedback was last edited;
 *                       equal to {@link #createdAt} for rows that
 *                       have not been edited; never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "teacherName",
        "lessonId",
        "lessonTitle",
        "quizAttemptId",
        "quizTitle",
        "feedbackType",
        "message",
        "createdAt",
        "updatedAt"
})
public record StudentTeacherFeedbackResponse(
        Long id,
        String teacherName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * fields the persistence layer guarantees ({@link #id},
     * {@link #message}, {@link #createdAt}, {@link #updatedAt}).
     * All remaining fields are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public StudentTeacherFeedbackResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
