package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.DragDropItem;

public interface DragDropItemRepository extends JpaRepository<DragDropItem, Long> {

    List<DragDropItem> findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(Long dragDropActivityId);

    @Modifying
    @Transactional
    @Query("delete from DragDropItem i where i.dragDropActivity.id = :activityId")
    int deleteByDragDropActivityId(@Param("activityId") Long dragDropActivityId);
}
