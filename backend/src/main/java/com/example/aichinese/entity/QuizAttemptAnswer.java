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
 * Per-question detail row for a {@link QuizAttempt}.
 *
 * <p>{@link #answerText} stores the student's raw submission as
 * {@code LONGTEXT}: for option-based questions this is a JSON-encoded
 * array of selected option ids; for free-text it is the verbatim
 * student answer. {@link #correct} is set by the grader and is the
 * single source of truth that the wrong-answer review flow filters on.</p>
 *
 * <p>The attempt → answer relationship is one-to-many: each question
 * shows up at most once per attempt, but the constraint is enforced
 * at the service layer (not via a unique index) so the grader can
 * append a corrective answer when a learner re-enters the same quiz.</p>
 *
 * <p>{@link #quizAttempt} and {@link #question} are mapped
 * {@code updatable = false}: an answer row is permanently bound to the
 * attempt and question it was recorded against. Corrective answers
 * are expressed as new rows, never as in-place rewrites of existing
 * ones, so the gradebook history stays auditable. {@link #answerText}
 * and {@link #correct} stay mutable to leave room for future
 * re-grading and partial-credit override flows.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quiz_attempt_answers", indexes = {
        @Index(name = "idx_quiz_attempt_answers_attempt_id", columnList = "quiz_attempt_id"),
        @Index(name = "idx_quiz_attempt_answers_question_id", columnList = "question_id")
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

    @Column(name = "correct", nullable = false)
    private boolean correct;
}
