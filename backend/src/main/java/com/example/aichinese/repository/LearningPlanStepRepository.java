package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.enums.PlanStepKind;

public interface LearningPlanStepRepository extends JpaRepository<LearningPlanStep, Long> {

    List<LearningPlanStep> findByPlanIdOrderByPositionAsc(Long planId);

    boolean existsByPlanIdAndStepKindAndRefId(Long planId, PlanStepKind stepKind, Long refId);

    /** Class badges on the catalogue belong to the same active plan as the learner's overview. */
    @Query("""
            select s.refId from LearningPlanStep s
            where s.stepKind = CLASS and s.plan.id = (
                select p.id from LearningPlan p where p.student.id = :studentId and p.active = true
                order by p.version desc limit 1)
            order by s.position
            """)
    List<Long> findActiveClassIds(@Param("studentId") Long studentId);

    /** The steps of the student's drafts; run before {@link LearningPlanRepository#deleteDrafts}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from LearningPlanStep step
            where step.plan.id in (select p.id from LearningPlan p where p.student.id = :studentId and p.confirmed = false)
            """)
    int deleteDraftSteps(@Param("studentId") Long studentId);

    /** Account deletion: SparkBot's "Why" on each step, written from the same goal. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LearningPlanStep step set step.reason = null
            where step.plan.id in (select p.id from LearningPlan p where p.student.user.id = :userId)
            """)
    int clearStepReasons(@Param("userId") Long userId);
}
