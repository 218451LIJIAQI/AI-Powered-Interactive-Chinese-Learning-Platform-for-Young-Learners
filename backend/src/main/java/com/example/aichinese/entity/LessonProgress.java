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

/**
 * Tracks a single student's progress through a single {@link Lesson}.
 *
 * <p>Exactly one row per {@code (student_id, lesson_id)} pair is
 * enforced by the unique constraint, so the dashboard can compute
 * "lessons completed / total" with a single index lookup.</p>
 *
 * <p>{@link #completionPercent} is updated incrementally as the
 * student watches videos, completes activities, or marks sections
 * done; it can be {@code null} for very early access where no
 * progress has been recorded yet (distinct from explicit
 * {@code 0%}).</p>
 *
 * <p>{@link #completed} is a hard boolean flipped to {@code true}
 * the first time {@link #completionPercent} reaches 100 — staying
 * {@code true} thereafter even if the student replays — so the
 * dashboard "lessons completed" count never decreases.</p>
 *
 * <p>{@link #version} prevents two concurrent progress writes (e.g.
 * the auto-saver and an explicit "mark complete" click) from
 * overwriting each other's increments.</p>
 */
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
