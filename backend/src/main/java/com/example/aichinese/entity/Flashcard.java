package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ContentStatus;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "flashcards", indexes = {
        @Index(name = "idx_flashcards_lesson_id", columnList = "lesson_id")
})
public class Flashcard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "front_text", nullable = false, length = 500)
    private String frontText;

    @Column(name = "back_text", nullable = false, length = 1000)
    private String backText;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Lob
    @Column(name = "extra_payload")
    private String extraPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;
}
