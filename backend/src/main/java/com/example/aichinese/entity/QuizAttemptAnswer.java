package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "quiz_attempt_answers", uniqueConstraints = {
        // SDD Table 4.5: one answer per question in an attempt (also enforced in the service).
        @UniqueConstraint(name = "uk_quiz_attempt_answers_attempt_question", columnNames = { "quiz_attempt_id", "question_id" })
})
public class QuizAttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_attempt_id", nullable = false, updatable = false)
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private QuizQuestion question;

    @Lob
    @Column(name = "answer_text", columnDefinition = "LONGTEXT")
    private String answerText;

    /** Option chosen for option-based questions (scored by option id); null for short-answer types. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id",
            foreignKey = @ForeignKey(name = "fk_quiz_attempt_answers_selected_option"))
    private QuizOption selectedOption;

    @Column(name = "correct", nullable = false)
    private boolean correct;
}
