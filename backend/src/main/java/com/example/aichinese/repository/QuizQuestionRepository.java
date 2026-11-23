package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    Optional<QuizQuestion> findByIdAndQuizId(Long id, Long quizId);

    long countByQuizId(Long quizId);

    @Query("""
            select q from QuizQuestion q where q.quiz.id = :quizId
            order by q.sortOrder, q.createdAt, q.id
            """)
    List<QuizQuestion> findInDisplayOrder(@Param("quizId") Long quizId);

    @Query("""
            select q from QuizQuestion q where q.quiz.id in :quizIds
            order by q.sortOrder, q.createdAt, q.id
            """)
    List<QuizQuestion> findInDisplayOrder(@Param("quizIds") Collection<Long> quizIds);

    @Query("""
            select q.quiz.id as id, count(q) as total from QuizQuestion q
            where q.quiz.id in :quizIds group by q.quiz.id
            """)
    List<IdCountProjection> countByQuiz(@Param("quizIds") Collection<Long> quizIds);
}
