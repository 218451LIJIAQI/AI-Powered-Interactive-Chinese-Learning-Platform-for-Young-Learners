package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;

/**
 * One question of a Practice quiz. {@code options} is a JSON array of {@code {"text": ..., "pinyin": ...}}; the
 * correct index never leaves the server before the student answers.
 */
@Getter
@Setter
@Entity
@Table(name = "practice_quiz_questions", indexes = {
        @Index(name = "idx_practice_quiz_questions_quiz", columnList = "practice_quiz_id,sort_order")
})
public class PracticeQuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_quiz_id", nullable = false, updatable = false)
    private PracticeQuiz practiceQuiz;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "prompt", nullable = false, length = 300)
    private String prompt;

    @Column(name = "hanzi", length = 100)
    private String hanzi;

    @Column(name = "pinyin", length = 200)
    private String pinyin;

    @Lob
    @Column(name = "options", nullable = false, columnDefinition = "TEXT")
    private String options;

    @Column(name = "correct_option_index", nullable = false)
    private Integer correctOptionIndex;

    @Column(name = "explanation", length = 500)
    private String explanation;
}
