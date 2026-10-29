package com.example.aichinese.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.MistakeSource;

/**
 * A question a student got wrong in Learn or Practice, waiting for the Mistakes review until it is answered right
 * there ({@code clearedAt}). The question is kept as a snapshot (prompt, choices, answer) so the review still works
 * after the lesson or set changes. {@code itemRef} names the source item, so a repeated mistake reopens the same row.
 */
@Getter
@Setter
@Entity
@Table(name = "learning_mistakes", indexes = {
        @Index(name = "idx_learning_mistakes_student_waiting", columnList = "student_id,cleared_at,last_wrong_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_mistakes_student_item", columnNames = { "student_id", "item_ref" })
})
public class LearningMistake extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "mistake_source", nullable = false, length = 20)
    private MistakeSource mistakeSource;

    /** e.g. {@code LEARN#12} (a Learn question) or {@code QUIZZES#3#41} (an item of a Practice set). */
    @Column(name = "item_ref", nullable = false, length = 120)
    private String itemRef;

    /** The lesson or set title, shown next to the mistake. */
    @Column(name = "source_title", nullable = false, length = 200)
    private String sourceTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_kind", nullable = false, length = 20)
    private AnswerKind answerKind;

    /** Learn question type whose instruction the interface writes; null when {@code prompt} is set. */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_kind", length = 30)
    private LearnQuestionType questionKind;

    /** The question as written in a Practice set ("What does 菜单 mean?"). */
    @Column(name = "prompt", length = 500)
    private String prompt;

    @Column(name = "hanzi", length = 200)
    private String hanzi;

    @Column(name = "pinyin", length = 300)
    private String pinyin;

    @Column(name = "meaning", length = 300)
    private String meaning;

    @Column(name = "audio_text", length = 200)
    private String audioText;

    @Lob
    @Column(name = "choices", nullable = false, columnDefinition = "TEXT")
    private String choices;

    @Column(name = "answer", nullable = false, length = 300)
    private String answer;

    @Column(name = "explanation", length = 500)
    private String explanation;

    /** The last wrong answer; null when the question was skipped. */
    @Column(name = "your_answer", length = 300)
    private String yourAnswer;

    @Column(name = "wrong_count", nullable = false)
    private Integer wrongCount = 1;

    @Column(name = "last_wrong_at", nullable = false)
    private LocalDateTime lastWrongAt;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;
}
