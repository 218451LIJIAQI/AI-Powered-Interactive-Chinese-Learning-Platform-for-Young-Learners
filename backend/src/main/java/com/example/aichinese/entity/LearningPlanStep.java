package com.example.aichinese.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.PlanStepKind;

/**
 * A step of a plan, in order: a Learn unit or a class from the real catalogue ({@code refId}). The title is a copy
 * taken when the plan was built, for when the unit or class later disappears. {@code reason} is SparkBot's "Why"
 * (null for a rule-based plan, whose reason is written from {@code goalPicks}).
 */
@Getter
@Setter
@Entity
@Table(name = "learning_plan_steps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_learning_plan_steps_plan_position", columnNames = { "plan_id", "position" })
})
public class LearningPlanStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private LearningPlan plan;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_kind", nullable = false, length = 20)
    private PlanStepKind stepKind;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "reason", length = 300)
    private String reason;

    /** The goals this step matches, comma-separated. */
    @Column(name = "goal_picks", nullable = false, length = 400)
    private String goalPicks = "";

    @Column(name = "optional_step", nullable = false)
    private boolean optionalStep;

    /** A unit the level check showed the student mostly knows: planned as a quick review. */
    @Column(name = "quick_review", nullable = false)
    private boolean quickReview;

    @Column(name = "week_from", nullable = false)
    private Integer weekFrom;

    @Column(name = "week_to", nullable = false)
    private Integer weekTo;
}
