package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttempt;
import java.util.Collection;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndStudentUserId(Long id, Long studentUserId);

    boolean existsByQuizIdAndSubmittedTrue(Long quizId);

    /** Submitted attempts of the student on quizzes of the given lessons, newest first. */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson" })
    List<QuizAttempt> findByStudentIdAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(
            Long studentId,
            Collection<Long> lessonIds);

    /** Submitted attempts of the students on quizzes of the given lessons, newest first. */
    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByStudentIdInAndQuizLessonIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(
            Collection<Long> studentIds,
            Collection<Long> lessonIds);

    /** Open (not yet submitted) attempts of the student on the quiz. */
    List<QuizAttempt> findByStudentIdAndQuizIdAndSubmittedFalseOrderByIdDesc(Long studentId, Long quizId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson" })
    List<QuizAttempt> findByStudentIdAndQuizIdAndSubmittedTrueOrderByCompletedAtDescIdDesc(Long studentId, Long quizId);

    @EntityGraph(attributePaths = { "student", "student.user" })
    List<QuizAttempt> findByQuizIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(Collection<Long> quizIds);
}
