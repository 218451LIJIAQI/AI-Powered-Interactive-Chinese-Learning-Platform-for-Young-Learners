package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.DragDropItem;

/**
 * Persistence operations for {@link DragDropItem} — the draggable cards
 * owned by a {@link com.example.aichinese.entity.DragDropActivity}.
 *
 * <p>The admin authoring flow rebuilds the full child set on every save
 * ({@code AdminActivityService.saveDragDropItems}), so the predominant
 * traffic pattern is "delete-all-then-insert-many" rather than per-row
 * mutation; the bulk delete defined here keeps that workflow O(1) round
 * trips instead of O(items).</p>
 */
public interface DragDropItemRepository extends JpaRepository<DragDropItem, Long> {

    /**
     * Returns every item belonging to {@code dragDropActivityId} in the
     * order the activity author intended. Sorts primarily by
     * {@code sortOrder} (the explicit reorderable position the admin
     * picks in the editor) with {@code createdAt} as a deterministic
     * tie-breaker for items the admin saved without changing the default
     * sort position \u2014 so two such items with equal {@code sortOrder}
     * still render in insertion order rather than in arbitrary database
     * order.
     */
    List<DragDropItem> findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(Long dragDropActivityId);

    /**
     * Bulk-removes every item attached to the given drag-drop activity in
     * a single SQL {@code DELETE}.
     *
     * <p>Implemented as an explicit {@link Modifying} JPQL query rather
     * than a derived {@code deleteBy*} method so Hibernate emits one
     * statement instead of the default "fetch-then-remove-per-row" path.
     * {@link DragDropItem} declares no {@code @PreRemove} lifecycle
     * callbacks and no cascading children, so the bulk DELETE is
     * semantically identical.</p>
     */
    @Modifying
    @Transactional
    @Query("delete from DragDropItem i where i.dragDropActivity.id = :activityId")
    int deleteByDragDropActivityId(@Param("activityId") Long dragDropActivityId);
}
