package com.example.aichinese.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.QuizQuestionType;

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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "quiz_questions", indexes = {
        @Index(name = "idx_quiz_questions_quiz_order", columnList = "quiz_id,sort_order,created_at")
})
public class QuizQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "question_type", nullable = false, length = 50)
    private QuizQuestionType questionType;

    @Lob
    @Column(name = "question_text", nullable = false, columnDefinition = "LONGTEXT")
    private String questionText;

    @Column(name = "correct_answer_text", length = 1000)
    private String correctAnswerText;

    @Column(name = "explanation", length = 2000)
    private String explanation;

    /** Optional hint (FRLRN10), shown to learners only on request and only in a quiz that allows hints. */
    @Column(name = "hint", length = 1000)
    private String hint;

    /** Recording a LISTEN_CHOOSE question plays (SDD 5.4); null for every other type. */
    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    /** Picture a MATCH_WORD_IMAGE question shows (SDD 5.4); null for every other type. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "points", nullable = false)
    private Integer points = 1;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * Added from the AI quiz draft and not checked yet: an admin keeps it as it is or edits it. A lesson is not
     * published while one of its questions is an unchecked AI draft.
     */
    @Column(name = "ai_draft", nullable = false)
    private boolean aiDraft;
}
