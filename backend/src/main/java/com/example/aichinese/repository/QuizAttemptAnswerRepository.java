package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttemptAnswer;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAsc(Long quizAttemptId);

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt", "quizAttempt.quiz" })
    List<QuizAttemptAnswer> findByQuizAttemptStudentIdAndCorrectFalseOrderByCreatedAtDesc(Long studentId);
}
