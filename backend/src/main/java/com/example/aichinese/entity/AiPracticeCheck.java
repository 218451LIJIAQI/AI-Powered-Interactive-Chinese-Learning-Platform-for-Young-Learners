package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PracticeCheckType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A pronunciation or handwriting practice score that SparkBot gave a student. Only the student sees it, and only the
 * result is kept: the recording or photo is never stored (it is held in memory while it is scored).
 * {@code tones/clarity/pace} are filled for recordings only; {@code tips} holds one "what to work on" point per line.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_practice_checks", indexes = {
        @Index(name = "idx_ai_practice_checks_student_created", columnList = "student_user_id,created_at"),
        @Index(name = "idx_ai_practice_checks_lesson_id", columnList = "lesson_id")
})
public class AiPracticeCheck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_practice_checks_student_user"))
    private User studentUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_ai_practice_checks_lesson"))
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, updatable = false, length = 20)
    private PracticeCheckType checkType;

    @Column(name = "target_text", nullable = false, updatable = false, length = 100)
    private String targetText;

    @Column(name = "score", nullable = false, updatable = false)
    private int score;

    @Column(name = "tones_score", updatable = false)
    private Integer tonesScore;

    @Column(name = "clarity_score", updatable = false)
    private Integer clarityScore;

    @Column(name = "pace_score", updatable = false)
    private Integer paceScore;

    @Column(name = "headline", nullable = false, updatable = false, length = 200)
    private String headline;

    @Column(name = "feedback", updatable = false, length = 1000)
    private String feedback;

    @Column(name = "tips", updatable = false, length = 2000)
    private String tips;
}
