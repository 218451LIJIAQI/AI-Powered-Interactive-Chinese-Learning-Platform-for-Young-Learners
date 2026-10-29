package com.example.aichinese.entity;

import com.example.aichinese.entity.base.TeacherArchivableContent;
import com.example.aichinese.entity.enums.LearningStage;

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
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_quizzes_status", columnList = "content_status"),
        @Index(name = "idx_quizzes_status_updated", columnList = "content_status,updated_at")
})
public class Quiz extends TeacherArchivableContent {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    private TeacherProfile teacher;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_stage", length = 20)
    private LearningStage learningStage;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "total_points")
    private Integer totalPoints;

    /** On a quiz of a lesson revision: the published quiz it was copied from (null for a quiz new in the revision). */
    @Column(name = "revision_source_id")
    private Long revisionSourceId;
}
