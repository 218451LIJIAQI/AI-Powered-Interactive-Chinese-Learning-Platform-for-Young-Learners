package com.example.aichinese.entity.base;

import com.example.aichinese.entity.User;
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
 * Columns shared by the six practice-activity entities (flashcards, listening, tracing, drag-and-drop,
 * matching and typing).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class PracticeActivityContent extends PublishableContent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_stage", length = 20)
    private LearningStage learningStage;

    @Column(name = "hsk_level")
    private Integer hskLevel;

    /** What finishing a Practice set pays (PracticeCatalog fills it). A class lesson's activity pays nothing. */
    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 0;
}
