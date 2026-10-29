package com.example.aichinese.entity;

import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "flashcards", indexes = {
        @Index(name = "idx_flashcards_status", columnList = "content_status"),
        @Index(name = "idx_flashcards_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_flashcards_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_flashcards_difficulty", columnList = "difficulty"),
        @Index(name = "idx_flashcards_created_by_user_id", columnList = "created_by_user_id"),
        @Index(name = "idx_flashcards_lesson", columnList = "lesson_id")
})
// A flashcard may have no title (the front text is shown instead), so this column alone is nullable.
@AttributeOverride(name = "title", column = @Column(name = "title", length = 200))
public class Flashcard extends PracticeActivityContent implements LessonActivity {

    @Column(name = "front_text", nullable = false, length = 500)
    private String frontText;

    @Column(name = "back_text", nullable = false, length = 1000)
    private String backText;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "pinyin", length = 255)
    private String pinyin;

    @Column(name = "english_meaning", length = 500)
    private String englishMeaning;

    @Column(name = "audio_url", length = 255)
    private String audioUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 10;
    /** The class lesson this activity belongs to; null for a practice-library set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "lesson_position")
    private Integer lessonPosition;

    @Column(name = "revision_source_id")
    private Long revisionSourceId;
}
