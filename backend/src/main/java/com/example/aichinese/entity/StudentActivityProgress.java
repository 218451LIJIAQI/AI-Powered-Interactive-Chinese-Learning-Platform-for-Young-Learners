package com.example.aichinese.entity;

import java.time.LocalDateTime;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_activity_progress", indexes = {
        @Index(name = "idx_student_activity_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_student_activity_progress_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_student_activity_progress_type_source", columnList = "activity_type, activity_source_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_activity_progress_student_activity",
                columnNames = { "student_id", "activity_type", "activity_source_id" })
})
public class StudentActivityProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "activity_source_id", nullable = false)
    private Long activitySourceId;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_interacted_at")
    private LocalDateTime lastInteractedAt;

    @Column(name = "completion_summary", length = 500)
    private String completionSummary;
}
