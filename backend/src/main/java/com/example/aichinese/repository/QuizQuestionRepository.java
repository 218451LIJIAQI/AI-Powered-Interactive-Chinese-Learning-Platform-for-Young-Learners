package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user" })
    List<QuizQuestion> findByQuizIdOrderBySortOrderAscCreatedAtAsc(Long quizId);

    List<QuizQuestion> findByQuizIdInOrderBySortOrderAscIdAsc(Collection<Long> quizIds);
}
