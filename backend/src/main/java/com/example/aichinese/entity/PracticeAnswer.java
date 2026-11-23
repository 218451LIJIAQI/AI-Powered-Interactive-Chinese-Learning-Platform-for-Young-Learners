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
import com.example.aichinese.entity.enums.AnswerConfidence;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * One checked Practice answer: whether it was right, how sure the student said they were and how many hint steps
 * they opened first. The Progress page counts these per week ("Sure and right", "Lucky guesses", "Sure but wrong",
 * "AI hints this week"). {@code createdAt} is when it was answered.
 */
@Getter
@Setter
@Entity
@Table(name = "practice_answers", indexes = {
        @Index(name = "idx_practice_answers_student_created", columnList = "student_id,created_at"),
        @Index(name = "idx_practice_answers_student_item_created", columnList = "student_id,item_ref,created_at")
})
public class PracticeAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_type", nullable = false, length = 20, updatable = false)
    private PracticeType practiceType;

    @Column(name = "item_ref", nullable = false, length = 120, updatable = false)
    private String itemRef;

    @Column(name = "correct", nullable = false, updatable = false)
    private boolean correct;

    /** Null for an answer that was skipped or for a type that does not ask (Matching). */
    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 10, updatable = false)
    private AnswerConfidence confidence;

    @Column(name = "hint_steps", nullable = false, updatable = false)
    private Integer hintSteps = 0;
}
