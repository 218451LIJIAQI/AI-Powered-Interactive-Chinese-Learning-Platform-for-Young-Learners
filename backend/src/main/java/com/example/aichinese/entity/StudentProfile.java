package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PlanSetupState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "student_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_student_profiles_user_id", columnNames = "user_id")
        }
)
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    /** XP a day that keeps the streak: 10, 20 or 30 (chosen in Account). */
    @Column(name = "daily_goal_xp", nullable = false)
    private Integer dailyGoalXp = 20;

    /** The learning plan setup: a new student is sent to it after sign-up; Home offers it again when skipped. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_setup_state", nullable = false, length = 20)
    private PlanSetupState planSetupState = PlanSetupState.NOT_STARTED;
}
