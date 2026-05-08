package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
}
