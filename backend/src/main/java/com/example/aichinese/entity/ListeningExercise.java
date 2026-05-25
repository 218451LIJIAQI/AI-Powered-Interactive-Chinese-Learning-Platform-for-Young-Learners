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
 * Audio-driven listening exercise in the platform practice library.
 *
 * <p>{@link #audioUrl} points at a hosted audio clip (currently a
 * platform CDN URL). {@link #questionPayload} is a JSON document
 * encoding the comprehension prompts and acceptable answers — held
 * in a single LONGTEXT column rather than a child table because the
 * payload is small and treated as an opaque blob by Hibernate; the
 * front-end is the canonical schema owner.</p>
 *
 * <p>Shares the activity metadata vocabulary with the other practice
 * library entities for uniform filtering.</p>
 *
 * <p>{@link #createdByUser} is mapped {@code updatable = false}:
 * authorship is captured once at insert time by
 * {@code AdminActivityMapper#toEntity} and intentionally not touched
 * again by the matching {@code applyTo} update method. All other
 * domain fields remain mutable through the admin moderation workflow.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "listening_exercises", indexes = {
        @Index(name = "idx_listening_exercises_status", columnList = "content_status"),
        @Index(name = "idx_listening_exercises_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_listening_exercises_age_band", columnList = "age_band"),
        @Index(name = "idx_listening_exercises_difficulty", columnList = "difficulty"),
        @Index(name = "idx_listening_exercises_created_by_user_id", columnList = "created_by_user_id")
})
public class ListeningExercise extends BaseEntity {

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
    private Integer xpReward = 15;

    @Column(name = "estimated_seconds")
    private Integer estimatedSeconds;
}
