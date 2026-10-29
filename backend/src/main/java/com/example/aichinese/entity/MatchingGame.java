package com.example.aichinese.entity;

import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;

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
@Table(name = "matching_games", indexes = {
        @Index(name = "idx_matching_games_status", columnList = "content_status"),
        @Index(name = "idx_matching_games_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_matching_games_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_matching_games_difficulty", columnList = "difficulty"),
        @Index(name = "idx_matching_games_created_by_user_id", columnList = "created_by_user_id"),
        @Index(name = "idx_matching_games_lesson", columnList = "lesson_id")
})
public class MatchingGame extends PracticeActivityContent implements LessonActivity {

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 15;
    /** The class lesson this activity belongs to; null for a practice-library set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "lesson_position")
    private Integer lessonPosition;

    @Column(name = "revision_source_id")
    private Long revisionSourceId;
}
