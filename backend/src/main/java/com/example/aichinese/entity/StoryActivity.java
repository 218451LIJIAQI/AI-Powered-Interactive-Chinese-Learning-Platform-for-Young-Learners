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

/**
 * Short-story reading activity in the platform practice library.
 *
 * <p>Carries the original Chinese narrative ({@link #storyText}),
 * optional pinyin transliteration ({@link #pinyin}), and optional
 * translation ({@link #translation}). After reading, the learner
 * answers a small set of {@link StoryQuestion} prompts that test
 * comprehension.</p>
 *
 * <p>Shares the activity metadata vocabulary with the other practice
 * library entities for uniform filtering.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "story_activities", indexes = {
        @Index(name = "idx_story_activities_status", columnList = "content_status"),
        @Index(name = "idx_story_activities_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_story_activities_age_band", columnList = "age_band"),
        @Index(name = "idx_story_activities_difficulty", columnList = "difficulty"),
        @Index(name = "idx_story_activities_created_by_user_id", columnList = "created_by_user_id")
})
public class StoryActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", length = 1000)
    private String instructions;

    /**
     * Required. The original Mandarin short-story body in simplified Chinese.
     */
    @Lob
    @Column(name = "story_text", columnDefinition = "LONGTEXT", nullable = false)
    private String storyText;

    /**
     * Optional Pinyin transliteration shown alongside the story to scaffold
     * young beginners.
     */
    @Lob
    @Column(name = "pinyin", columnDefinition = "TEXT")
    private String pinyin;

    /**
     * Optional English (or Bahasa Melayu) translation displayed under a
     * "translate" toggle.
     */
    @Lob
    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

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
    private Integer xpReward = 20;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;
}
