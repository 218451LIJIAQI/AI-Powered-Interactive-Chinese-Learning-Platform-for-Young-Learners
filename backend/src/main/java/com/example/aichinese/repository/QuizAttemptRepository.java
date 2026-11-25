package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    @EntityGraph(attributePaths = "quiz.lesson")
    Optional<QuizAttempt> findByIdAndStudentId(Long id, Long studentId);

    boolean existsByQuizIdAndSubmittedTrue(Long quizId);

    @Query("""
            select distinct a.quiz.id from QuizAttempt a
            where a.quiz.id in :quizIds and a.submitted = true
            """)
    Set<Long> findSubmittedQuizIds(@Param("quizIds") Collection<Long> quizIds);

    /** The student's submitted attempts on the given quizzes, newest first. */
    List<QuizAttempt> findByStudentIdAndQuizIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(
            Long studentId,
            Collection<Long> quizIds);

    /** The most recent open attempt of the student on the quiz. */
    Optional<QuizAttempt> findFirstByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(Long studentId, Long quizId);

    @Query("""
            select distinct a.quiz.id from QuizAttempt a
            where a.student.id = :studentId and a.quiz.id in :quizIds and a.submitted = false
            """)
    Set<Long> findOpenQuizIds(@Param("studentId") Long studentId, @Param("quizIds") Collection<Long> quizIds);
}
