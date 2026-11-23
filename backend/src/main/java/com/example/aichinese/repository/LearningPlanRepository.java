package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.repository.projection.XiaomeiPlanGoal;

import jakarta.persistence.LockModeType;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {

    Optional<LearningPlan> findFirstByStudentIdAndActiveTrueOrderByVersionDesc(Long studentId);

    @Query("""
            select p.placedLevel from LearningPlan p where p.student = :student and p.active = true
            order by p.version desc limit 1
            """)
    Optional<MandarinLevel> findActivePlacement(@Param("student") StudentProfile student);

    /** Only the goal used in Xiaomei conversations, looked up by the signed-in account. */
    Optional<XiaomeiPlanGoal> findFirstByStudentUserIdAndActiveTrueOrderByVersionDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearningPlan> findFirstLockedByStudentIdAndActiveTrueOrderByVersionDesc(Long studentId);

    /** Latest version through a current read, including commits newer than an existing MySQL snapshot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p.version from LearningPlan p where p.student.id = :studentId order by p.version desc limit 1")
    Optional<Integer> findLatestVersionForUpdate(@Param("studentId") Long studentId);

    /** The plan the setup built and the learner has not started yet ("Start my path"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearningPlan> findFirstLockedByStudentIdAndConfirmedFalseOrderByVersionDesc(Long studentId);

    Optional<LearningPlan> findFirstByStudentIdAndConfirmedFalseOrderByVersionDesc(Long studentId);

    /** Removes the student's plans that were built but never started. Run after {@link LearningPlanStepRepository#deleteDraftSteps}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LearningPlan p where p.student.id = :studentId and p.confirmed = false")
    int deleteDrafts(@Param("studentId") Long studentId);

    /** Keeps earlier versions but makes none of them the student's plan. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LearningPlan p set p.active = false where p.student.id = :studentId and p.active = true")
    int deactivateAll(@Param("studentId") Long studentId);

    /** Account deletion: the goal in the student's own words, and what SparkBot wrote from it, is removed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LearningPlan p
            set p.goalText = null, p.reasonText = null, p.goalTitle = null, p.goalSummary = null,
                p.workOn = null, p.outcomes = null, p.milestoneText = null
            where p.student.id in (select s.id from StudentProfile s where s.user.id = :userId)
            """)
    int clearOwnWords(@Param("userId") Long userId);
}
