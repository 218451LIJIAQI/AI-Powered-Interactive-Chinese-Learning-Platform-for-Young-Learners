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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lesson_progress", indexes = {
        @Index(name = "idx_lesson_progress_student_id", columnList = "student_id"),
        @Index(name = "idx_lesson_progress_lesson_id", columnList = "lesson_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_lesson_progress_student_lesson", columnNames = { "student_id", "lesson_id" })
})
public class LessonProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "completion_percent")
    private Integer completionPercent;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
}
