package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

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
@Table(name = "listening_exercises", indexes = {
        @Index(name = "idx_listening_exercises_status", columnList = "content_status"),
        @Index(name = "idx_listening_exercises_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_listening_exercises_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_listening_exercises_difficulty", columnList = "difficulty"),
        @Index(name = "idx_listening_exercises_created_by_user_id", columnList = "created_by_user_id")
})
public class ListeningExercise extends BaseEntity implements PracticeActivityContent {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "audio_url", length = 255)
    private String audioUrl;

    @Lob
    @Column(name = "question_payload", columnDefinition = "LONGTEXT")
    private String questionPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

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

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 15;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;
}
