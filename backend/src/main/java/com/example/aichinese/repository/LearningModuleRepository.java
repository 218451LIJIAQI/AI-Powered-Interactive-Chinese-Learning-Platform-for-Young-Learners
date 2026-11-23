package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningModule;

public interface LearningModuleRepository extends JpaRepository<LearningModule, Long> {
}
