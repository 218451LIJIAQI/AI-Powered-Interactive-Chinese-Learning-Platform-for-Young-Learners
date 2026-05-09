package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TracingExercise;

public interface TracingExerciseRepository extends JpaRepository<TracingExercise, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    List<TracingExercise> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    Optional<TracingExercise> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);
}
