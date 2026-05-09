package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    List<Flashcard> findByLessonIdOrderBySortOrderAscCreatedAtAsc(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    Optional<Flashcard> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    long countByLessonId(Long lessonId);
}
