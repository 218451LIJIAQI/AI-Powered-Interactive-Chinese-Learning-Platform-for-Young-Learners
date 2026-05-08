package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttemptAnswer;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {
}
