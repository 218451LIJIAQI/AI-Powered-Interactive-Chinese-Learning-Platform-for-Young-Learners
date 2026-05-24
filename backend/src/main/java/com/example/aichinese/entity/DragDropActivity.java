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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-curated drag-and-drop activity in the platform-managed
 * student practice library.
 *
 * <p>One of six sibling activity types — together with
 * {@link Flashcard}, {@link ListeningExercise}, {@link MatchingGame},
 * {@link StoryActivity} and {@link TracingExercise} — that share the
 * same metadata vocabulary ({@link #ageBand}, {@link #difficulty},
 * {@link #hskLevel}, {@link #topic}, {@link #tags}, {@link #xpReward})
 * so the student-facing library can filter and surface them
 * uniformly.</p>
 *
 * <p>Children {@link DragDropItem} rows carry the actual draggables
 * and target zones. {@link #xpReward} defaults to 15 and is the base
 * XP a learner earns on first successful completion; the actual
 * awarded amount is multiplied by
 * {@link com.example.aichinese.entity.enums.ActivityDifficulty#xpMultiplier()}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "drag_drop_activities", indexes = {
        @Index(name = "idx_drag_drop_activities_status", columnList = "content_status"),
        @Index(name = "idx_drag_drop_activities_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_drag_drop_activities_age_band", columnList = "age_band"),
        @Index(name = "idx_drag_drop_activities_difficulty", columnList = "difficulty"),
        @Index(name = "idx_drag_drop_activities_created_by_user_id", columnList = "created_by_user_id")
})
public class DragDropActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "instructions", length = 1000)
    private String instructions;

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
