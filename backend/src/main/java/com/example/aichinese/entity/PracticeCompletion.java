package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * One finished run of a Practice set (every run, right or wrong): "sets done today", "played by N students" and
 * whether a set is done (every item right at least once) come from these rows. The set lives in the table of its
 * type, so {@code activityId} is not a foreign key.
 */
@Getter
@Setter
@Entity
@Table(name = "practice_completions", indexes = {
        @Index(name = "idx_practice_completions_student_created", columnList = "student_id,created_at"),
        @Index(name = "idx_practice_completions_set", columnList = "practice_type,activity_id")
})
public class PracticeCompletion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_type", nullable = false, length = 20, updatable = false)
    private PracticeType practiceType;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private Long activityId;

    @Column(name = "correct_count", nullable = false, updatable = false)
    private Integer correctCount;

    @Column(name = "item_count", nullable = false, updatable = false)
    private Integer itemCount;

    @Column(name = "xp_awarded", nullable = false, updatable = false)
    private Integer xpAwarded = 0;

    public boolean allCorrect() {
        return itemCount != null && itemCount > 0 && itemCount.equals(correctCount);
    }
}
