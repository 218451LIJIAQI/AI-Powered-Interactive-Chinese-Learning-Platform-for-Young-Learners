package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.DragDropItem;

public interface DragDropItemRepository extends JpaRepository<DragDropItem, Long> {

    List<DragDropItem> findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(Long dragDropActivityId);

    void deleteByDragDropActivityId(Long dragDropActivityId);
}
