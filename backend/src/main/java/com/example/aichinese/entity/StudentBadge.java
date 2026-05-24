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

/**
 * Award row recording that a {@link StudentProfile} has earned a
 * particular {@link Badge}.
 *
 * <p>The unique constraint on {@code (student_id, badge_id)} enforces
 * the "earn each badge at most once" rule at the database layer — a
 * duplicate insert from a retried gamification rule simply triggers a
 * {@code DuplicateKeyException} that the service silently swallows.</p>
 *
 * <p>{@link #awardedAt} captures the wall-clock instant of the award.
 * The default field initializer {@code LocalDateTime.now()} is
 * intentional: badge awards are typically created in a single
 * transaction with no test fixture overriding the timestamp, and
 * keeping the initializer here (rather than a {@code @PrePersist}
 * hook) simplifies the gamification rule code that reads
 * {@link #getAwardedAt()} immediately after construction.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_badges", indexes = {
        @Index(name = "idx_student_badges_student_id", columnList = "student_id"),
        @Index(name = "idx_student_badges_badge_id", columnList = "badge_id"),
        @Index(name = "idx_student_badges_student_awarded", columnList = "student_id,awarded_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_badges_student_badge", columnNames = { "student_id", "badge_id" })
})
public class StudentBadge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "badge_id", nullable = false)
    private Badge badge;

    @Column(name = "awarded_at", nullable = false)
    private LocalDateTime awardedAt = LocalDateTime.now();
}
