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
 * One finished run of a Practice set. A set is done after a run with every item correct.
 * The practice type determines which table holds {@code activityId}.
 */
@Getter
@Setter
@Entity
@Table(name = "practice_completions", indexes = {
        @Index(name = "idx_practice_completions_set", columnList = "practice_type,activity_id"),
        @Index(name = "idx_practice_completions_student_type_activity", columnList = "student_id,practice_type,activity_id"),
        @Index(name = "idx_practice_completions_created_at", columnList = "created_at")
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
}
