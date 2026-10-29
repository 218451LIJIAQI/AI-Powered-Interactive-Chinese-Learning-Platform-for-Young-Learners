package com.example.aichinese.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;

/** A Learn stop a student finished (a checkpoint only once it is passed); the next stop unlocks from it. */
@Getter
@Setter
@Entity
@Table(name = "learn_lesson_completions", indexes = {
        @Index(name = "idx_learn_lesson_completions_lesson", columnList = "lesson_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_learn_lesson_completions_student_lesson", columnNames = { "student_id", "lesson_id" })
})
public class LearnLessonCompletion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private LearnLesson lesson;

    @Column(name = "best_accuracy", nullable = false)
    private Integer bestAccuracy = 0;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "xp_awarded", nullable = false)
    private Integer xpAwarded = 0;

    @Column(name = "first_completed_at", nullable = false)
    private LocalDateTime firstCompletedAt;

    @Column(name = "last_completed_at", nullable = false)
    private LocalDateTime lastCompletedAt;
}
