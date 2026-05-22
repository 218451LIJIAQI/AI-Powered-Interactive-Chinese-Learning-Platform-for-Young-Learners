package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
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
        @Index(name = "idx_flashcards_status", columnList = "content_status"),
        @Index(name = "idx_flashcards_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_flashcards_age_band", columnList = "age_band"),
        @Index(name = "idx_flashcards_difficulty", columnList = "difficulty"),
        @Index(name = "idx_flashcards_created_by_user_id", columnList = "created_by_user_id")
})
public class Flashcard extends BaseEntity {

    /**
     * Admin user who authored or curates this card. Nullable to preserve
     * legacy rows that pre-date the platform library.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "title", length = 200)
    private String title;

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

    @Column(name = "malay_meaning", length = 500)
    private String malayMeaning;

    @Column(name = "audio_url", length = 255)
    private String audioUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Lob
    @Column(name = "extra_payload", columnDefinition = "LONGTEXT")
    private String extraPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "hsk_level")
    private Integer hskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_band", length = 30)
    private AgeBand ageBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    private ActivityDifficulty difficulty;

    @Column(name = "topic", length = 100)
    private String topic;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 10;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;
}
