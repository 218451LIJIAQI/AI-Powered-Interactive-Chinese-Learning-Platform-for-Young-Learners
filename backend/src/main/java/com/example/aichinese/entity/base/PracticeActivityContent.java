package com.example.aichinese.entity.base;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Columns shared by the six practice-activity entities (flashcards, matching games, listening,
 * tracing, drag-and-drop and story activities). Each entity keeps its own {@code xp_reward}
 * column because the default reward differs per type.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class PracticeActivityContent extends ReviewableContent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_stage", length = 20)
    private LearningStage learningStage;

    @Column(name = "hsk_level")
    private Integer hskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 20)
    private ActivityDifficulty difficulty;

    @Column(name = "topic", length = 100)
    private String topic;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;

    public abstract Integer getXpReward();

    public abstract void setXpReward(Integer xpReward);
}
