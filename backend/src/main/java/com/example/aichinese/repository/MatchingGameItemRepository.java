package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.MatchingGameItem;

/**
 * Persistence operations for {@link MatchingGameItem} — the left/right
 * pairs that make up a {@link com.example.aichinese.entity.MatchingGame}.
 *
 * <p>The admin authoring flow rebuilds the full child set on every save
 * ({@code AdminActivityService.saveMatchingGameItems}), so the predominant
 * traffic pattern is "delete-all-then-insert-many" rather than per-row
 * mutation; the bulk delete defined here keeps that workflow O(1) round
 * trips instead of O(items).</p>
 */
public interface MatchingGameItemRepository extends JpaRepository<MatchingGameItem, Long> {

    List<MatchingGameItem> findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(Long matchingGameId);

    /**
     * Bulk-removes every pair attached to the given matching game in a
     * single SQL {@code DELETE}.
     *
     * <p>Implemented as an explicit {@link Modifying} JPQL query rather
     * than a derived {@code deleteBy*} method so Hibernate emits one
     * statement instead of the default "fetch-then-remove-per-row" path.
     * {@link MatchingGameItem} declares no {@code @PreRemove} lifecycle
     * callbacks and no cascading children, so the bulk DELETE is
     * semantically identical.</p>
     */
    @Modifying
    @Transactional
    @Query("delete from MatchingGameItem i where i.matchingGame.id = :gameId")
    int deleteByMatchingGameId(@Param("gameId") Long matchingGameId);
}
