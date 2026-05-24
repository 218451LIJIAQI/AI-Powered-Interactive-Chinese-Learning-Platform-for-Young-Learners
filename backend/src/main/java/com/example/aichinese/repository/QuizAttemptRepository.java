package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findByQuizTeacherIdOrderByCompletedAtDescCreatedAtDesc(Long teacherId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user", "student.currentLevel" })
    List<QuizAttempt> findByQuizTeacherIdAndStudentIdInOrderByCompletedAtDescCreatedAtDesc(Long teacherId, List<Long> studentIds);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Page<QuizAttempt> findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(Long quizId, Pageable pageable);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByQuizIdAndSubmittedTrueOrderByCompletedAtDescCreatedAtDesc(Long quizId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndStudentUserId(Long id, Long studentUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "quiz.teacher", "quiz.teacher.user", "student", "student.user" })
    Optional<QuizAttempt> findByIdAndQuizTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "quiz", "quiz.lesson", "student", "student.user" })
    List<QuizAttempt> findByStudentIdOrderByCompletedAtDescCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndQuizTeacherUserId(Long studentId, Long teacherUserId);

    boolean existsByStudentIdAndQuizLessonIdAndQuizTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);

    boolean existsByQuizIdAndSubmittedTrue(Long quizId);
}
