package com.example.aichinese.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.value.PronunciationFeedback;

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
 * The old score columns hold historical transcript estimates. New recordings have structured acoustic feedback.
 * {@code tips} holds one "what to work on" point per line. The unused columns {@code shape_score}
 * and {@code tone_detail} of V1 are dropped by V2 where empty (kept unmapped where an older version stored values).
 * {@code deletedAt} is set while a deleted check can still be brought back (the Undo of Delete): it is out of the
 * student's list then, and deleted for good once the Undo window has passed.
 */
@Getter
@Setter
@Entity
@Table(name = "ai_practice_checks", indexes = {
        @Index(name = "idx_ai_practice_checks_student_deleted_created", columnList = "student_user_id,deleted_at,created_at,id")
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pronunciation", updatable = false)
    private PronunciationFeedback pronunciation;

    @Column(name = "headline", nullable = false, updatable = false, length = 200)
    private String headline;

    @Column(name = "feedback", updatable = false, length = 1000)
    private String feedback;

    @Column(name = "tips", updatable = false, length = 2000)
    private String tips;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
