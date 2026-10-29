package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A key phrase of a class lesson; students hear it with Listen and review it in the lesson's flashcards. */
@Getter
@Setter
@Entity
@Table(name = "lesson_key_phrases", indexes = {
        @Index(name = "idx_lesson_key_phrases_lesson", columnList = "lesson_id,sort_order")
})
public class LessonKeyPhrase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "chinese", nullable = false, length = 100)
    private String chinese;

    @Column(name = "pinyin", nullable = false, length = 200)
    private String pinyin;

    @Column(name = "meaning", nullable = false, length = 300)
    private String meaning;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
