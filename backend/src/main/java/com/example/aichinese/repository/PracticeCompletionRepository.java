package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.enums.PracticeType;

public interface PracticeCompletionRepository extends JpaRepository<PracticeCompletion, Long> {

    /** A set is done once the student has a fully correct, nonempty run. */
    @Query("""
            select exists (select 1 from PracticeCompletion c
                where c.student.id = :studentId and c.practiceType = :type and c.activityId = :activityId
                and c.itemCount > 0 and c.correctCount = c.itemCount)
            """)
    boolean hasSuccessfulRun(@Param("studentId") Long studentId,
            @Param("type") PracticeType type, @Param("activityId") Long activityId);

    /**
     * The different sets (type and id) the student successfully finished at least once: all items right, or any
     * non-empty flashcard review. Zero-correct / all-skipped runs do not count toward milestones or badges.
     */
    @Query("""
            select count(distinct c.practiceType, c.activityId) from PracticeCompletion c
            where c.student.id = :studentId
              and c.itemCount > 0
              and (c.correctCount = c.itemCount
                   or c.practiceType = com.example.aichinese.entity.enums.PracticeType.FLASHCARDS)
            """)
    long countFinishedSets(@Param("studentId") Long studentId);

    @Query("""
            select count(distinct c.student.id) from PracticeCompletion c
            where c.practiceType = :type and c.activityId = :activityId
            """)
    long countStudentsWhoPlayed(@Param("type") PracticeType type, @Param("activityId") Long activityId);
}
