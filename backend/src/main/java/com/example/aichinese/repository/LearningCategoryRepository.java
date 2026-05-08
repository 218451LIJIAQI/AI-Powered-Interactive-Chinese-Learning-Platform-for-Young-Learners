package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningCategory;

public interface LearningCategoryRepository extends JpaRepository<LearningCategory, Long> {

    Optional<LearningCategory> findByName(String name);
}
