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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "quiz_attempts", indexes = {
        @Index(name = "idx_quiz_attempts_quiz_id", columnList = "quiz_id"),
        @Index(name = "idx_quiz_attempts_student_id", columnList = "student_id"),
        @Index(name = "idx_quiz_attempts_completed_at", columnList = "completed_at")
})
public class QuizAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false, updatable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "submitted", nullable = false)
    private boolean submitted;

    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** End of a timed attempt; an open attempt past it is submitted with the answers saved so far. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
