package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PracticeActivityContent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "story_activities", indexes = {
        @Index(name = "idx_story_activities_status", columnList = "content_status"),
        @Index(name = "idx_story_activities_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_story_activities_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_story_activities_difficulty", columnList = "difficulty"),
        @Index(name = "idx_story_activities_created_by_user_id", columnList = "created_by_user_id")
})
public class StoryActivity extends PracticeActivityContent {

    @Lob
    @Column(name = "story_text", columnDefinition = "LONGTEXT", nullable = false)
    private String storyText;

    @Lob
    @Column(name = "pinyin", columnDefinition = "TEXT")
    private String pinyin;

    @Lob
    @Column(name = "translation", columnDefinition = "TEXT")
    private String translation;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 20;
}
