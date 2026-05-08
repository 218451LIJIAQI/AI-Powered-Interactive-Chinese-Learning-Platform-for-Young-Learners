package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
}
