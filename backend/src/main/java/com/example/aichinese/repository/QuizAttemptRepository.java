package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
}
