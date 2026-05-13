package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findTop5ByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<QuizAttempt> findTop5ByQuizTeacherIdOrderByCreatedAtDesc(Long teacherId);

    List<QuizAttempt> findByQuizTeacherIdOrderByCompletedAtDescCreatedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByQuizIdOrderByCompletedAtDescCreatedAtDesc(Long quizId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndStudentUserId(Long id, Long studentUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByStudentIdOrderByCompletedAtDescCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndQuizTeacherUserId(Long studentId, Long teacherUserId);

    @Query("select avg(qa.score * 100.0 / qa.maxScore) from QuizAttempt qa where qa.student.id = :studentId and qa.submitted = true and qa.maxScore is not null and qa.maxScore > 0 and qa.score is not null")
    Double findAveragePercentageByStudentId(@Param("studentId") Long studentId);

    @Query("select avg(qa.score * 1.0) from QuizAttempt qa where qa.quiz.teacher.id = :teacherId and qa.score is not null")
    Double findAverageScoreByTeacherId(@Param("teacherId") Long teacherId);

    @Query("select avg(qa.score * 100.0 / qa.maxScore) from QuizAttempt qa where qa.quiz.teacher.id = :teacherId and qa.submitted = true and qa.maxScore is not null and qa.maxScore > 0 and qa.score is not null")
    Double findAveragePercentageByTeacherId(@Param("teacherId") Long teacherId);
}
