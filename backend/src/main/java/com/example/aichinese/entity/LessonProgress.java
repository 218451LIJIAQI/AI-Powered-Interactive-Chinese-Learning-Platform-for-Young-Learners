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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lesson_progress", indexes = {
        @Index(name = "idx_lesson_progress_student_updated", columnList = "student_id,updated_at"),
        @Index(name = "idx_lesson_progress_completed_at", columnList = "completed,completed_at"),
        @Index(name = "idx_lesson_progress_last_accessed", columnList = "last_accessed_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_lesson_progress_student_lesson", columnNames = { "student_id", "lesson_id" })
})
public class LessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private Lesson lesson;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /** When the lesson became complete (null while it is not); admin analytics count completions by it. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
