package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LearningPlan;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {

    Optional<LearningPlan> findFirstByStudentIdAndActiveTrueOrderByVersionDesc(Long studentId);

    @Query("select coalesce(max(p.version), 0) from LearningPlan p where p.student.id = :studentId")
    int maxVersion(@Param("studentId") Long studentId);


    /** Keeps earlier versions but makes none of them the student's plan. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LearningPlan p set p.active = false where p.student.id = :studentId and p.active = true")
    int deactivateAll(@Param("studentId") Long studentId);

    /** Account deletion: the goal in the student's own words, and what SparkBot wrote from it, is removed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LearningPlan p set p.goalText = null, p.reasonText = null, p.goalTitle = null, p.goalSummary = null
            where p.student.id in (select s.id from StudentProfile s where s.user.id = :userId)
            """)
    int clearOwnWords(@Param("userId") Long userId);
}
