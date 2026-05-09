package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.ListeningExercise;

public interface ListeningExerciseRepository extends JpaRepository<ListeningExercise, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    List<ListeningExercise> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    Optional<ListeningExercise> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);
}
