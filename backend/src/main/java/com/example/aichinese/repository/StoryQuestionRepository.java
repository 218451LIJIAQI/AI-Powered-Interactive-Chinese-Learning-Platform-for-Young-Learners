package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.StoryQuestion;

/**
 * Persistence operations for {@link StoryQuestion} — the comprehension
 * prompts attached to a {@link com.example.aichinese.entity.StoryActivity}.
 *
 * <p>The admin authoring flow rebuilds the full prompt set on every save
 * ({@code AdminActivityService.saveStoryQuestions}), so the predominant
 * traffic pattern is "delete-all-then-insert-many" rather than per-row
 * mutation; the bulk delete defined here keeps that workflow O(1) round
 * trips instead of O(questions).</p>
 */
public interface StoryQuestionRepository extends JpaRepository<StoryQuestion, Long> {

    /**
     * Returns every prompt belonging to {@code storyActivityId} in the
     * order the author intended. Sorts primarily by {@code sortOrder}
     * with {@code createdAt} as a deterministic tie-breaker so two
     * prompts sharing the same sort order still render in insertion
     * order rather than in arbitrary database order.
     */
    List<StoryQuestion> findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(Long storyActivityId);

    /**
     * Bulk-removes every prompt attached to the given story activity in
     * a single SQL {@code DELETE}.
     *
     * <p>Implemented as an explicit {@link Modifying} JPQL query rather
     * than a derived {@code deleteBy*} method so Hibernate emits one
     * statement instead of the default "fetch-then-remove-per-row" path.
     * {@link StoryQuestion} declares no {@code @PreRemove} lifecycle
     * callbacks and no cascading children, so the bulk DELETE is
     * semantically identical.</p>
     */
    @Modifying
    @Transactional
    @Query("delete from StoryQuestion q where q.storyActivity.id = :storyActivityId")
    int deleteByStoryActivityId(@Param("storyActivityId") Long storyActivityId);
}
