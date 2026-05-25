package com.example.aichinese.entity;

import java.time.LocalDate;

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
 * Per-student tracker of consecutive-day learning streaks.
 *
 * <p>Exactly one row per student (enforced by the unique constraint on
 * {@code student_id}) so the gamification engine can read and update
 * a learner's streak in O(1).</p>
 *
 * <p>The streak rule is implemented in {@code GamificationService}:
 * if {@link #lastLearningDate} equals today, no-op; if it equals
 * yesterday, increment {@link #currentStreak} and bump
 * {@link #bestStreak} when overtaken; otherwise the streak resets to 1.</p>
 *
 * <p>{@link #version} prevents two concurrent learning events from
 * the same student (e.g. quiz submission and lesson completion in
 * parallel browser tabs) from double-incrementing the streak.</p>
 *
 * <h3>Field mutability contract</h3>
 * <ul>
 *   <li>{@link #student} is mapped {@code updatable = false}: a streak
 *       row is permanently bound to one learner. Re-targeting would
 *       additionally collide with {@code uk_daily_streaks_student},
 *       so the ORM-layer guard simply makes that contract explicit.</li>
 *   <li>{@link #currentStreak}, {@link #bestStreak},
 *       {@link #lastLearningDate} are mutated on every learning event
 *       by {@code StudentLearningProgressService}; the
 *       {@link Version @Version} field above protects those writes
 *       against lost-update races.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "daily_streaks", indexes = {
        @Index(name = "idx_daily_streaks_student_id", columnList = "student_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_streaks_student", columnNames = { "student_id" })
})
public class DailyStreak extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @Column(name = "best_streak", nullable = false)
    private Integer bestStreak = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "last_learning_date")
    private LocalDate lastLearningDate;
}
