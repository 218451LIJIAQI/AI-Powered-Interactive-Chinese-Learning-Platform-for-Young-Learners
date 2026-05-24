package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Learner-specific extension of {@link User}.
 *
 * <p>Keeps role-only fields (display avatar, school, total XP, current
 * learning level) out of the authentication root and lazily loaded —
 * the unique {@link jakarta.persistence.OneToOne} on {@code user_id}
 * guarantees at most one student profile per user.</p>
 *
 * <p>{@link #totalPoints} is a denormalized cache of
 * {@code SUM(points_delta)} on {@link PointsTransaction} for this
 * student; the ledger is the source of truth. The field is updated
 * synchronously inside the same transaction that inserts the points
 * transaction, so a crashed write does not silently lie to the
 * dashboard.</p>
 *
 * <p>{@link #displayName} overrides {@link User#getDisplayName()} for
 * student-facing UI (so a parent can set a friendly nickname distinct
 * from the formal account name); when {@code null} the UI falls back
 * to the {@code User} display name.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_profiles", indexes = {
        @Index(name = "idx_student_profiles_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_student_profiles_current_level_id", columnList = "current_level_id")
})
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_level_id")
    private LearningLevel currentLevel;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "school_name", length = 150)
    private String schoolName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;
}
