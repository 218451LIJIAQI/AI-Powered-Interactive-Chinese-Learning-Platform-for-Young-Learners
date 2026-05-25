package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single question inside a {@link Quiz}.
 *
 * <p>{@link #questionType} is a free-form short string that maps to
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}; option-based
 * types ({@code MULTIPLE_CHOICE}, {@code TRUE_FALSE}, etc.) require
 * one or more {@link QuizOption} children, while free-text and
 * structured types are graded against {@link #correctAnswerText}.</p>
 *
 * <p>{@link #points} is the maximum score the question contributes;
 * the per-quiz total is denormalized into {@link Quiz#getTotalPoints()}.
 * {@link #sortOrder} drives the canonical question order during a
 * student attempt; the {@link QuizAttempt} flow may optionally
 * shuffle.</p>
 *
 * <p>{@link #quiz} is mapped {@code updatable = false}: a question
 * belongs to a single parent quiz for its entire lifetime. Moving a
 * question between quizzes is expressed by deleting and re-creating
 * (so the {@link Quiz#getTotalPoints()} cache stays consistent), not
 * by re-parenting in place.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quiz_questions", indexes = {
        @Index(name = "idx_quiz_questions_quiz_id", columnList = "quiz_id")
})
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false, updatable = false)
    private Quiz quiz;

    @Column(name = "question_type", nullable = false, length = 50)
    private String questionType;

    @Lob
    @Column(name = "question_text", nullable = false, columnDefinition = "LONGTEXT")
    private String questionText;

    @Column(name = "correct_answer_text", length = 1000)
    private String correctAnswerText;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "points", nullable = false)
    private Integer points = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
