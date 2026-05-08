package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LessonActivity;

public interface LessonActivityRepository extends JpaRepository<LessonActivity, Long> {

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    List<LessonActivity> findByLessonIdAndContentStatusNotOrderBySortOrderAscCreatedAtAsc(Long lessonId, com.example.aichinese.entity.enums.ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user" })
    Optional<LessonActivity> findByIdAndLessonTeacherUserId(Long activityId, Long teacherUserId);
}
