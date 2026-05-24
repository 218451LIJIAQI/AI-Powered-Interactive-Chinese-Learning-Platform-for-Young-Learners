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
 * Single answer alternative for an option-based {@link QuizQuestion}.
 *
 * <p>Multiple options may have {@link #correctOption} set to {@code true}
 * (multi-select questions); the auto-grader requires the student's
 * selected set to exactly match the {@code correctOption} set for a
 * fully-correct mark, with partial-credit policy controlled at the
 * service layer.</p>
 *
 * <p>{@link #optionText} is {@code LONGTEXT} so authors can include
 * code snippets, formatted Chinese passages, or rich explanations as
 * options when needed.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quiz_options", indexes = {
        @Index(name = "idx_quiz_options_question_id", columnList = "question_id")
})
public class QuizOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Lob
    @Column(name = "option_text", nullable = false, columnDefinition = "LONGTEXT")
    private String optionText;

    @Column(name = "correct_option", nullable = false)
    private boolean correctOption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
