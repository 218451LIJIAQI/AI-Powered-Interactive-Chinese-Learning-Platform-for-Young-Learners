package com.example.aichinese.entity;

import com.example.aichinese.entity.base.LessonActivity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "flashcards", indexes = {
        @Index(name = "idx_flashcards_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_flashcards_hsk_level", columnList = "hsk_level")
})
// The schema has always allowed a flashcard set without a title, so this column alone is nullable.
@AttributeOverride(name = "title", column = @Column(name = "title", length = 200))
public class Flashcard extends LessonActivity {
}
