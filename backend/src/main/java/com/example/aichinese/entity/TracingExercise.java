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
@Table(name = "tracing_exercises", indexes = {
        @Index(name = "idx_tracing_exercises_status", columnList = "content_status"),
        @Index(name = "idx_tracing_exercises_status_updated", columnList = "content_status,updated_at"),
        @Index(name = "idx_tracing_exercises_hsk_level", columnList = "hsk_level"),
        @Index(name = "idx_tracing_exercises_difficulty", columnList = "difficulty"),
        @Index(name = "idx_tracing_exercises_created_by_user_id", columnList = "created_by_user_id"),
        @Index(name = "idx_tracing_exercises_lesson", columnList = "lesson_id")
})
public class TracingExercise extends PracticeActivityContent implements LessonActivity {

    @Column(name = "target_character", length = 100)
    private String targetCharacter;

    @Column(name = "pinyin", length = 255)
    private String pinyin;

    @Column(name = "meaning", length = 500)
    private String meaning;

    /** Strokes of all target characters together; the pad asks for this many before Done. */
    @Column(name = "stroke_count")
    private Integer strokeCount;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 12;
    /** The class lesson this activity belongs to; null for a practice-library set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Column(name = "lesson_position")
    private Integer lessonPosition;

    @Column(name = "revision_source_id")
    private Long revisionSourceId;
}
