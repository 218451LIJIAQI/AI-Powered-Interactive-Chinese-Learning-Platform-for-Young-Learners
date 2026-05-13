package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

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
    @NonNull
    List<TeacherFeedback> findAll();

    /**
     * Sort-aware variant used by the admin feedback list, so ordering happens
     * in the database rather than in JVM after loading every row. The
     * EntityGraph mirrors the eager-fetch list of {@link #findAll()} so the
     * response mapper does not regress into per-row lazy loading.
     */
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
    @NonNull
    List<TeacherFeedback> findAll(@NonNull Sort sort);

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
