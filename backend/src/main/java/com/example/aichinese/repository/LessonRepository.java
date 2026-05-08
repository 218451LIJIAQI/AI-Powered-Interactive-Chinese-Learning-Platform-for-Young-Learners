package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
}
