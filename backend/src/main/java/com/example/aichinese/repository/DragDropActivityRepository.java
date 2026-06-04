package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.DragDropActivity;

public interface DragDropActivityRepository
        extends JpaRepository<DragDropActivity, Long>, JpaSpecificationExecutor<DragDropActivity> {
}
