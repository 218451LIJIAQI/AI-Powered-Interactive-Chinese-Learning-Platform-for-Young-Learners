package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttemptAnswer;
import java.util.Optional;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

    @EntityGraph(attributePaths = { "question", "question.quiz", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdOrderByCreatedAtAsc(Long quizAttemptId);

    Optional<QuizAttemptAnswer> findByQuizAttemptIdAndQuestionId(Long quizAttemptId, Long questionId);

    @EntityGraph(attributePaths = { "question", "quizAttempt" })
    List<QuizAttemptAnswer> findByQuizAttemptIdIn(Collection<Long> quizAttemptIds);
}
