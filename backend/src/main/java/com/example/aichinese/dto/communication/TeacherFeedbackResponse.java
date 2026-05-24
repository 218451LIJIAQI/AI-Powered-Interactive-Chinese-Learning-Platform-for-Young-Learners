package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Teacher-facing feedback row returned by the teacher-side feedback
 * endpoints under {@code /api/v1/teachers/feedback}:
 * <ul>
 *   <li>{@code GET /api/v1/teachers/feedback} — paginated history
 *       of the calling teacher's authored feedback, newest first.</li>
 *   <li>{@code POST /api/v1/teachers/feedback} — echoes the freshly
 *       created row from {@link CreateTeacherFeedbackRequest}.</li>
 *   <li>{@code PUT /api/v1/teachers/feedback/{id}} — echoes the
 *       freshly updated row from
 *       {@link UpdateTeacherFeedbackRequest}.</li>
 * </ul>
 *
 * <p>There is intentionally no delete endpoint — once submitted,
 * feedback is immutable apart from edits so it becomes a stable
 * record the receiving student can rely on (see
 * {@link com.example.aichinese.controller.TeacherFeedbackController}
 * Javadoc).</p>
 *
 * <p>Surfaces both the teacher and student identity (denormalised
 * {@code displayName} from {@code users}) plus the optional lesson
 * / quiz target (with denormalised titles) so the teacher's
 * "Feedback I've given" page renders without follow-up lookups.</p>
 *
 * <p>The student-facing counterpart
 * {@link StudentTeacherFeedbackResponse} omits the student identity
 * (the student is the caller) and slims the teacher identity to the
 * display name only.</p>
 *
 * <h2>Target linkage</h2>
 * <p>{@link #lessonId} / {@link #lessonTitle} are populated when the
 * feedback targets a lesson (either directly or via the quiz's
 * parent lesson). {@link #quizAttemptId} / {@link #quizTitle} are
 * populated when the feedback targets a quiz attempt. Both pairs
 * may be {@code null} for general feedback attached to the
 * (teacher, student) pair without a specific lesson or quiz
 * touch-point.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #message}, {@link #createdAt} and
 *       {@link #updatedAt} are always non-{@code null} — they map
 *       to the primary key, the {@code NOT NULL} feedback message
 *       column, and the
 *       {@link com.example.aichinese.entity.base.BaseEntity}
 *       managed timestamps.</li>
 *   <li>{@link #teacherId} / {@link #studentId} are non-{@code null}
 *       in practice (both ManyToOne associations are
 *       {@code optional=false}) but the mapper defensively guards
 *       against detached entities, so consumers should still
 *       null-check before navigating.</li>
 *   <li>{@link #teacherName} / {@link #studentName} may be
 *       {@code null} when the corresponding {@code User} has no
 *       display name set.</li>
 *   <li>{@link #lessonId} / {@link #lessonTitle} /
 *       {@link #quizAttemptId} / {@link #quizTitle} are
 *       independently nullable.</li>
 *   <li>{@link #feedbackType} may be {@code null} on legacy rows;
 *       otherwise it is the {@code .name()} of a member of
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
 * @param teacherId      primary key of the authoring
 *                       {@link com.example.aichinese.entity.TeacherProfile};
 *                       in practice non-{@code null}
 * @param teacherName    display name of the authoring teacher;
 *                       may be {@code null}
 * @param studentId      primary key of the addressed
 *                       {@link com.example.aichinese.entity.StudentProfile};
 *                       in practice non-{@code null}
 * @param studentName    display name of the addressed student;
 *                       may be {@code null}
 * @param lessonId       primary key of the linked
 *                       {@link com.example.aichinese.entity.Lesson};
 *                       {@code null} when no lesson is linked
 * @param lessonTitle    title of the linked lesson;
 *                       {@code null} when no lesson is linked
 * @param quizAttemptId  primary key of the linked
 *                       {@link com.example.aichinese.entity.QuizAttempt};
 *                       {@code null} when no quiz attempt is linked
 * @param quizTitle      title of the linked quiz;
 *                       {@code null} when no quiz attempt is linked
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
        "teacherId",
        "teacherName",
        "studentId",
        "studentName",
        "lessonId",
        "lessonTitle",
        "quizAttemptId",
        "quizTitle",
        "feedbackType",
        "message",
        "createdAt",
        "updatedAt"
})
public record TeacherFeedbackResponse(
        Long id,
        Long teacherId,
        String teacherName,
        Long studentId,
        String studentName,
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
     * fields the persistence layer unconditionally guarantees
     * ({@link #id}, {@link #message}, {@link #createdAt},
     * {@link #updatedAt}). The teacher / student / lesson / quiz
     * identifier fields are intentionally permitted to be
     * {@code null} so the mapper can serialise detached or
     * partially-loaded entities without throwing.
     */
    public TeacherFeedbackResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
