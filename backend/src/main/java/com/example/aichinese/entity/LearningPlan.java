package com.example.aichinese.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanSelfLevel;

/**
 * One version of a student's learning plan: the goal in the student's words and picks, the pace, the level the
 * level check placed them at, and what SparkBot wrote (goal title and summary, skills to work on, outcomes, a first
 * milestone), null when the plan was built from rules. A new goal or a changed level makes a new version; earlier
 * versions are kept inactive, and progress is never stored here (it is read from the student's real completions).
 * Lists are stored as text: goals, focus and skills comma-separated, AI lists one item per line.
 */
@Getter
@Setter
@Entity
@Table(name = "learning_plans", indexes = {
        @Index(name = "idx_learning_plans_student_active", columnList = "student_id,active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_plans_student_version", columnNames = { "student_id", "version" })
})
public class LearningPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "version", nullable = false, updatable = false)
    private Integer version;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_reason", nullable = false, length = 20)
    private PlanReason reason;

    /** The student's own words for "Something else". */
    @Column(name = "reason_text", length = 120)
    private String reasonText;

    @Column(name = "goal_picks", nullable = false, length = 400)
    private String goalPicks;

    /** The goal in the student's own words (up to 200 characters). */
    @Column(name = "goal_text", length = 200)
    private String goalText;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_pace", nullable = false, length = 20)
    private PlanPace pace;

    @Column(name = "focus_skills", nullable = false, length = 60)
    private String focusSkills;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_level", nullable = false, length = 20)
    private PlanSelfLevel selfLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "placed_level", nullable = false, length = 20)
    private MandarinLevel placedLevel;

    @Column(name = "head_start", nullable = false)
    private boolean headStart;

    @Column(name = "known_skills", nullable = false, length = 200)
    private String knownSkills = "";

    @Column(name = "new_skills", nullable = false, length = 200)
    private String newSkills = "";

    /** When the level check was taken; null when the student was brand new and skipped it. */
    @Column(name = "level_checked_at")
    private LocalDateTime levelCheckedAt;

    @Column(name = "level_check_answered", nullable = false)
    private Integer levelCheckAnswered = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false, length = 20)
    private MandarinLevel targetLevel;

    /** True when SparkBot wrote the texts and ordered the steps; false for a plan built from rules. */
    @Column(name = "ai_assisted", nullable = false)
    private boolean aiAssisted;

    @Column(name = "goal_title", length = 80)
    private String goalTitle;

    @Column(name = "goal_summary", length = 500)
    private String goalSummary;

    @Lob
    @Column(name = "work_on", columnDefinition = "TEXT")
    private String workOn;

    @Lob
    @Column(name = "outcomes", columnDefinition = "TEXT")
    private String outcomes;

    /** The timeline is shorter than the path needs, so the plan names a first milestone. */
    @Column(name = "ambitious", nullable = false)
    private boolean ambitious;

    @Column(name = "milestone_week")
    private Integer milestoneWeek;

    @Column(name = "milestone_text", length = 400)
    private String milestoneText;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "total_weeks", nullable = false)
    private Integer totalWeeks;

    /** The week the last Learn step should end; the weeks after it are for review and practice. */
    @Column(name = "content_weeks", nullable = false)
    private Integer contentWeeks;
}
