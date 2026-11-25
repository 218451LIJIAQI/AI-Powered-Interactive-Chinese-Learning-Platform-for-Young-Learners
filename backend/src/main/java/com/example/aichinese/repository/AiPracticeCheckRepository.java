package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiPracticeCheck;

public interface AiPracticeCheckRepository extends JpaRepository<AiPracticeCheck, Long> {

    /** The student's Recent checks: the checks that are not deleted, newest first. */
    @EntityGraph(attributePaths = "lesson")
    Page<AiPracticeCheck> findByStudentUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long studentUserId, Pageable pageable);

    /** One of the student's checks, deleted or not. */
    @EntityGraph(attributePaths = "lesson")
    Optional<AiPracticeCheck> findByIdAndStudentUserId(Long id, Long studentUserId);

    /** This student's checks deleted before {@code cutoff}: their Undo is over, so they are deleted for good. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AiPracticeCheck c where c.studentUser.id = :studentUserId and c.deletedAt < :cutoff")
    int deleteDeletedBefore(@Param("studentUserId") Long studentUserId, @Param("cutoff") LocalDateTime cutoff);

    /** Removes every practice check belonging to a deleted learner account. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiPracticeCheck c where c.studentUser.id = :studentUserId")
    int deleteByStudentUserId(@Param("studentUserId") Long studentUserId);
}
