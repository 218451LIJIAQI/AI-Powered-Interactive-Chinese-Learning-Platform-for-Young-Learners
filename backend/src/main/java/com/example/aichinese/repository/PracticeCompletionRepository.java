package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.enums.PracticeType;

public interface PracticeCompletionRepository extends JpaRepository<PracticeCompletion, Long> {

    List<PracticeCompletion> findByStudentIdAndPracticeTypeAndActivityIdIn(
            Long studentId, PracticeType practiceType, Collection<Long> activityIds);

    long countByStudentIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long studentId, LocalDateTime from, LocalDateTime to);

    /** The different sets (type and id) the student finished at least once. */
    @Query("select distinct c.practiceType, c.activityId from PracticeCompletion c where c.student.id = :studentId")
    List<Object[]> findFinishedSets(@Param("studentId") Long studentId);

    @Query("""
            select count(distinct c.student.id) from PracticeCompletion c
            where c.practiceType = :type and c.activityId = :activityId
            """)
    long countStudentsWhoPlayed(@Param("type") PracticeType type, @Param("activityId") Long activityId);
}
