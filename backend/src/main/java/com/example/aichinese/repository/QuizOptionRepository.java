package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.QuizOption;

/**
 * Persistence operations for {@link QuizOption} — the multiple-choice
 * answer alternatives owned by a
 * {@link com.example.aichinese.entity.QuizQuestion}.
 *
 * <p>The teacher quiz authoring flow ({@code TeacherQuizService}) rebuilds
 * the full option list whenever a question is updated, so the predominant
 * traffic pattern is "delete-all-then-insert-many" rather than per-row
 * mutation; the bulk delete defined here keeps that workflow O(1) round
 * trips instead of O(options).</p>
 */
public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    /**
     * Returns every option belonging to {@code questionId} in the order
     * the author intended. Sorts primarily by {@code sortOrder} (the
     * explicit position the teacher picks) with {@code createdAt} as a
     * deterministic tie-breaker so two options sharing the same
     * sort order still render in insertion order rather than in
     * arbitrary database order.
     */
    List<QuizOption> findByQuestionIdOrderBySortOrderAscCreatedAtAsc(Long questionId);

    /**
     * Bulk-removes every option attached to the given quiz question in a
     * single SQL {@code DELETE}.
     *
     * <p>Implemented as an explicit {@link Modifying} JPQL query rather
     * than a derived {@code deleteBy*} method so Hibernate emits one
     * statement instead of the default "fetch-then-remove-per-row" path.
     * {@link QuizOption} declares no {@code @PreRemove} lifecycle
     * callbacks and no cascading children, so the bulk DELETE is
     * semantically identical.</p>
     */
    @Modifying
    @Transactional
    @Query("delete from QuizOption o where o.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
