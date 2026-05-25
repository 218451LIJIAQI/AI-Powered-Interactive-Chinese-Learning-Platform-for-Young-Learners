package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
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
 * Teacher-authored quiz attached to a {@link Lesson}.
 *
 * <p>A quiz is a thin header that owns one or more {@link QuizQuestion}
 * children (each potentially with {@link QuizOption} alternatives).
 * Student attempts are recorded in {@link QuizAttempt} rows, with
 * per-question answers captured in {@link QuizAttemptAnswer}.</p>
 *
 * <p>{@link #lesson} is nullable to support standalone practice quizzes
 * that don't belong to any specific lesson; {@link #teacher} records
 * the author for revenue attribution and access control.</p>
 *
 * <p>{@link #timeLimitMinutes} when non-null enables a countdown timer
 * on the student side; {@link #totalPoints} is denormalized from the
 * sum of {@link QuizQuestion#getPoints()} values for fast catalogue
 * rendering — kept in sync by {@code TeacherQuizService} on every
 * question save.</p>
 *
 * <p>{@link #lesson} and {@link #teacher} are mapped
 * {@code updatable = false}: the quiz's anchor lesson and authorship
 * are fixed at creation time. Verified against
 * {@code TeacherQuizService#updateQuiz}, which only mutates title,
 * description and {@code timeLimitMinutes}. Re-targeting either field
 * would silently transfer authorship or split a quiz from its lesson's
 * access-control gate, so the ORM-layer guard rules it out.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_lesson_id", columnList = "lesson_id"),
        @Index(name = "idx_quizzes_status", columnList = "content_status")
})
public class Quiz extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", updatable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", updatable = false)
    private TeacherProfile teacher;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 30)
    private ContentStatus contentStatus = ContentStatus.DRAFT;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "total_points")
    private Integer totalPoints;
}
