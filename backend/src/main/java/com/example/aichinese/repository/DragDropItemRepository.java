package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.DragDropItem;

public interface DragDropItemRepository extends JpaRepository<DragDropItem, Long> {

    @Query("""
            select i from DragDropItem i where i.dragDropActivity.id = :activityId
            order by i.sortOrder, i.createdAt, i.id
            """)
    List<DragDropItem> findInDisplayOrder(@Param("activityId") Long activityId);
}
