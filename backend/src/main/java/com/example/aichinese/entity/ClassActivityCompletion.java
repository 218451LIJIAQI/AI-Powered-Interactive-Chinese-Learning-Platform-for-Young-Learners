package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.ClassActivityType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A student finished an activity of a class lesson (counts toward the lesson's steps; classes give no XP). The
 * activity lives in the table of its type, so it is referenced by type and id.
 */
@Getter
@Setter
@Entity
@Table(name = "class_activity_completions", indexes = {
        @Index(name = "idx_class_activity_completions_lesson", columnList = "lesson_id,student_id")
}, uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_class_activity_completions_student_activity",
                columnNames = { "student_id", "activity_type", "activity_id" })
})
public class ClassActivityCompletion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, updatable = false, length = 20)
    private ClassActivityType activityType;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private Long activityId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "times_completed", nullable = false)
    private Integer timesCompleted = 1;
}
