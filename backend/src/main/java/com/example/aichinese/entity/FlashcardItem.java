package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;

/**
 * One card of a flashcard set (a Practice "Flashcards" set has several cards; its first card is also kept in the
 * flashcard's own front and back text).
 */
@Getter
@Setter
@Entity
@Table(name = "flashcard_items", indexes = {
        @Index(name = "idx_flashcard_items_flashcard", columnList = "flashcard_id,sort_order")
})
public class FlashcardItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flashcard_id", nullable = false, updatable = false)
    private Flashcard flashcard;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "hanzi", nullable = false, length = 100)
    private String hanzi;

    @Column(name = "pinyin", length = 200)
    private String pinyin;

    @Column(name = "meaning", nullable = false, length = 200)
    private String meaning;

    /** An example sentence using the word, with its meaning. */
    @Column(name = "example", length = 200)
    private String example;

    @Column(name = "example_meaning", length = 300)
    private String exampleMeaning;
}
