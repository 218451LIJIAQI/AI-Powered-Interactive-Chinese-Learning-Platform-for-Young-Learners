package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SavedItem;

public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {

    List<SavedItem> findTop5ByStudentIdOrderByCreatedAtDesc(Long studentId);
}
