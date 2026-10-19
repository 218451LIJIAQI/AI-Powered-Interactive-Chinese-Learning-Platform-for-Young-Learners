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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "student_profiles",
        indexes = {
                @Index(name = "idx_student_profiles_current_level_id", columnList = "current_level_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_student_profiles_user_id", columnNames = "user_id")
        }
)
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_level_id")
    private LearningLevel currentLevel;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "country", length = 100)
    private String country;

    /**
     * Optional institution (university, college or workplace) entered by the learner. Stored in the
     * legacy {@code school_name} column, which is kept to avoid a destructive schema change.
     */
    @Column(name = "school_name", length = 150)
    private String institution;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;
}
