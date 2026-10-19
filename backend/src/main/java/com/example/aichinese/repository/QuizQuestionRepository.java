package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user" })
    List<QuizQuestion> findByQuizIdOrderBySortOrderAscCreatedAtAsc(Long quizId);

    long countByQuizId(Long quizId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user" })
    Optional<QuizQuestion> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);
}
