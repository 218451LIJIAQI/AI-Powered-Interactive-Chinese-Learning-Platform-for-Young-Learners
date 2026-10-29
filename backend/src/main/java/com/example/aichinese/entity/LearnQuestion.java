package com.example.aichinese.entity;

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

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.LearnQuestionType;

/**
 * One question of a Learn lesson. {@code choices} is a JSON array: the options of a choice question
 * (objects with {@code text} and {@code pinyin}) or the word tiles of a build question (strings). The answer never
 * leaves the server before the student answers.
 */
@Getter
@Setter
@Entity
@Table(name = "learn_questions", indexes = {
        @Index(name = "idx_learn_questions_lesson", columnList = "lesson_id,position")
})
public class LearnQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private LearnLesson lesson;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_kind", nullable = false, length = 30)
    private LearnQuestionType questionKind;

    @Column(name = "hanzi", length = 200)
    private String hanzi;

    @Column(name = "pinyin", length = 300)
    private String pinyin;

    @Column(name = "meaning", length = 300)
    private String meaning;

    /** What speech synthesis reads aloud (zh-CN) for the listening types. */
    @Column(name = "audio_text", length = 200)
    private String audioText;

    @Lob
    @Column(name = "choices", nullable = false, columnDefinition = "TEXT")
    private String choices;

    /** The right option text, or the sentence tiles in order separated by spaces. */
    @Column(name = "answer", nullable = false, length = 300)
    private String answer;

    @Column(name = "explanation", length = 500)
    private String explanation;
}
