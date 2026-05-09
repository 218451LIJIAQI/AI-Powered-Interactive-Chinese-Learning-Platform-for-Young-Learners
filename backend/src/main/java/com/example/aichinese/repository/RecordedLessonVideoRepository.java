package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RecordedLessonVideo;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    @Override
    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findAll();

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    List<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonIdAndActiveTrue(Long id, Long lessonId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "lesson", "lesson.teacher", "lesson.teacher.user", "coursePackage", "uploadedFile" })
    Optional<RecordedLessonVideo> findByIdAndActiveTrue(Long id);
}
