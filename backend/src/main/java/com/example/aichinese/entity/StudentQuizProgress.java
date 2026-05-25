package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Roll-up row that aggregates a student's history of attempts on a
 * single {@link Quiz}.
 *
 * <p>One row exists per {@code (student_id, quiz_id)} pair — enforced
 * by the unique constraint — and is upserted by the grader after every
 * {@link QuizAttempt}. The denormalisation lets dashboards fetch
 * "best score per quiz" without aggregating the entire attempt
 * history table on every read.</p>
 *
 * <p>{@link #quizId} is held as a plain column rather than a
 * {@code @ManyToOne} so that deleting a quiz does not orphan the
 * progress row; the dashboard tolerates dangling references when a
 * historical quiz is removed.</p>
 *
 * <p>{@link #completionSummary} is a free-form short JSON-or-text
 * payload (e.g. "best=8/10 attempts=3") used by the analytics tile
 * without re-querying the attempt detail.</p>
 *
 * <h3>Field mutability contract</h3>
 * <ul>
 *   <li><b>Immutable</b>: {@link #student}, {@link #lesson},
 *       {@link #quizId}, {@link #startedAt} — the row's identity
 *       (which student, which lesson, which historical quiz, the
 *       moment learning began) is fixed once the row is created and
 *       additionally protected by {@code uk_student_quiz_progress_student_quiz}.</li>
 *   <li><b>Mutable</b>: {@link #completed}, {@link #attemptCount},
 *       {@link #completedAt}, {@link #lastInteractedAt},
 *       {@link #completionSummary} — upserted by the grader after
 *       every attempt.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_quiz_progress", indexes = {
        @Index(name = "idx_student_quiz_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_student_quiz_progress_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_student_quiz_progress_quiz_id", columnList = "quiz_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_quiz_progress_student_quiz",
                columnNames = { "student_id", "quiz_id" })
})
public class StudentQuizProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private Lesson lesson;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private Long quizId;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_interacted_at")
    private LocalDateTime lastInteractedAt;

    @Column(name = "completion_summary", length = 500)
    private String completionSummary;
}
