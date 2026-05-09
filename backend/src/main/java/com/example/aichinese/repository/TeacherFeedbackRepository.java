package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TeacherFeedback;

public interface TeacherFeedbackRepository extends JpaRepository<TeacherFeedback, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "teacher",
            "teacher.user",
            "student",
            "student.user",
            "lesson",
            "quizAttempt",
            "quizAttempt.quiz",
            "quizAttempt.quiz.lesson"
    })
    List<TeacherFeedback> findAll();

    @EntityGraph(attributePaths = {
            "teacher",
            "teacher.user",
            "student",
            "student.user",
            "lesson",
            "quizAttempt",
            "quizAttempt.quiz",
            "quizAttempt.quiz.lesson"
    })
    List<TeacherFeedback> findByTeacherUserIdOrderByCreatedAtDesc(Long teacherUserId);

    @EntityGraph(attributePaths = {
            "teacher",
            "teacher.user",
            "student",
            "student.user",
            "lesson",
            "quizAttempt",
            "quizAttempt.quiz",
            "quizAttempt.quiz.lesson"
    })
    List<TeacherFeedback> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);

    @EntityGraph(attributePaths = {
            "teacher",
            "teacher.user",
            "student",
            "student.user",
            "lesson",
            "quizAttempt",
            "quizAttempt.quiz",
            "quizAttempt.quiz.lesson"
    })
    Optional<TeacherFeedback> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = {
            "teacher",
            "teacher.user",
            "student",
            "student.user",
            "lesson",
            "quizAttempt",
            "quizAttempt.quiz",
            "quizAttempt.quiz.lesson"
    })
    Optional<TeacherFeedback> findByIdAndStudentUserId(Long id, Long studentUserId);
}
