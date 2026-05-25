package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-text feedback message authored by a {@link TeacherProfile} for a
 * single {@link StudentProfile}.
 *
 * <p>The optional {@link #lesson} and {@link #quizAttempt} references
 * scope the comment to a particular learning artefact (so the student
 * UI can surface "feedback on Quiz 3" rather than a generic note).
 * Both being null is valid — that captures a freestanding comment from
 * a teacher dashboard.</p>
 *
 * <p>{@link #feedbackType} is a free-form short string that maps to
 * {@link com.example.aichinese.entity.enums.TeacherFeedbackType}; the
 * column is loose to accommodate legacy data without a Flyway
 * migration. The application layer parses it through
 * {@code TeacherFeedbackType#tryFrom}.</p>
 *
 * <p>Creation triggers a {@link Notification} of type
 * {@code TEACHER_FEEDBACK} so the student is alerted in real time.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teacher_feedback", indexes = {
        @Index(name = "idx_teacher_feedback_teacher_id", columnList = "teacher_id"),
        @Index(name = "idx_teacher_feedback_student_id", columnList = "student_id"),
        @Index(name = "idx_teacher_feedback_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_teacher_feedback_quiz_attempt_id", columnList = "quiz_attempt_id")
})
@NamedEntityGraph(
        // Single source of truth for the eager-fetch attribute set used
        // by every read in TeacherFeedbackRepository: teacher dashboards,
        // student feedback list and admin moderation all need
        // teacher / teacher.user / student / student.user / lesson and
        // the quiz-attempt chain hydrated in one round trip.
        name = TeacherFeedback.GRAPH_DETAIL,
        attributeNodes = {
                @NamedAttributeNode(value = "teacher", subgraph = "TeacherFeedback.teacherWithUser"),
                @NamedAttributeNode(value = "student", subgraph = "TeacherFeedback.studentWithUser"),
                @NamedAttributeNode("lesson"),
                @NamedAttributeNode(value = "quizAttempt", subgraph = "TeacherFeedback.quizAttemptChain")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "TeacherFeedback.teacherWithUser",
                        attributeNodes = @NamedAttributeNode("user")),
                @NamedSubgraph(
                        name = "TeacherFeedback.studentWithUser",
                        attributeNodes = @NamedAttributeNode("user")),
                @NamedSubgraph(
                        name = "TeacherFeedback.quizAttemptChain",
                        attributeNodes = {
                                @NamedAttributeNode(value = "quiz", subgraph = "TeacherFeedback.quizWithLesson")
                        }),
                @NamedSubgraph(
                        name = "TeacherFeedback.quizWithLesson",
                        attributeNodes = @NamedAttributeNode("lesson"))
        }
)
public class TeacherFeedback extends BaseEntity {

    /**
     * Name of the {@link NamedEntityGraph} that eagerly fetches the
     * standard attribute set required by every read endpoint that
     * surfaces a {@code TeacherFeedback}: teacher (with user),
     * student (with user), lesson, and quizAttempt (with quiz +
     * quiz.lesson).
     */
    public static final String GRAPH_DETAIL = "TeacherFeedback.detail";

    /**
     * Author. Mapped {@code updatable = false}: a feedback record always
     * stays attributed to its original author. Re-targeting authorship
     * is blocked at the ORM layer so audit trails remain coherent.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    /**
     * Recipient. Mapped {@code updatable = false}: feedback is
     * permanently addressed to its original recipient. Sending the same
     * message to a different student would create a fresh row.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    /**
     * Optional quiz-attempt anchor. Mapped {@code updatable = false}:
     * the scoped artefact is part of the feedback's identity ("teacher
     * feedback on this specific attempt"); detaching it would change
     * the dashboard surface where the feedback shows up.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_attempt_id", updatable = false)
    private QuizAttempt quizAttempt;

    /**
     * Optional lesson anchor. Same {@code updatable = false} rationale
     * as {@link #quizAttempt} — the surface where students see the
     * feedback is fixed at creation time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false)
    private Lesson lesson;

    @Column(name = "feedback_type", length = 50)
    private String feedbackType;

    @Lob
    @Column(name = "feedback_message", nullable = false, columnDefinition = "LONGTEXT")
    private String feedbackMessage;
}
