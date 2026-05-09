package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.MatchingGame;

public interface MatchingGameRepository extends JpaRepository<MatchingGame, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    List<MatchingGame> findByLessonIdOrderByCreatedAtAsc(Long lessonId);

    long countByLessonId(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    Optional<MatchingGame> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);
}
