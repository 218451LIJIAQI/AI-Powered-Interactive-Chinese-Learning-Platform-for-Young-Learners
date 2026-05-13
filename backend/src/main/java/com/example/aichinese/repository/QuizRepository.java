package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    List<Quiz> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "teacher", "teacher.user" })
    Optional<Quiz> findByIdAndTeacherUserId(Long id, Long teacherUserId);
}
