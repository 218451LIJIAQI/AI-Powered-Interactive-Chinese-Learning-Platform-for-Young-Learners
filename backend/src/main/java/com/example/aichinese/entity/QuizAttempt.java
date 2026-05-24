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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single student attempt at a {@link Quiz}.
 *
 * <p>An attempt starts as {@link #submitted} = {@code false} when the
 * student opens the quiz and is flipped to {@code true} when they
 * submit (manually or via a timer expiry). {@link #completedAt} is
 * stamped at the same moment and becomes the canonical timestamp the
 * dashboard sorts on.</p>
 *
 * <p>{@link #score} and {@link #maxScore} are computed by the grader
 * after submission. Until then both remain {@code null}, which the
 * UI distinguishes from {@code 0} to display "in progress" rather
 * than "scored zero". Per-question detail lives in
 * {@link QuizAttemptAnswer}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_quiz_attempts_quiz_id", columnList = "quiz_id"),
        @Index(name = "idx_quiz_attempts_student_id", columnList = "student_id"),
        @Index(name = "idx_quiz_attempts_completed_at", columnList = "completed_at")
})
public class QuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "submitted", nullable = false)
    private boolean submitted;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
