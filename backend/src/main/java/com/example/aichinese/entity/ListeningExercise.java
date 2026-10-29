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
@Table(name = "listening_exercises", indexes = {
        @Index(name = "idx_listening_exercises_status", columnList = "content_status"),
        @Index(name = "idx_listening_exercises_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_listening_exercises_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_listening_exercises_difficulty", columnList = "difficulty"),
        @Index(name = "idx_listening_exercises_created_by_user_id", columnList = "created_by_user_id")
})
public class ListeningExercise extends PracticeActivityContent {

    @Column(name = "audio_url", length = 255)
    private String audioUrl;

    @Lob
    @Column(name = "question_payload", columnDefinition = "LONGTEXT")
    private String questionPayload;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 15;
}
