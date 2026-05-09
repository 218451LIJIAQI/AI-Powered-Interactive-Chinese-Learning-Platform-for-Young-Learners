package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentActivityProgress;

public interface StudentActivityProgressRepository extends JpaRepository<StudentActivityProgress, Long> {

    @EntityGraph(attributePaths = { "lesson" })
    List<StudentActivityProgress> findByStudentIdAndLessonIdOrderByUpdatedAtDesc(Long studentId, Long lessonId);

    @EntityGraph(attributePaths = { "lesson" })
    Optional<StudentActivityProgress> findByStudentIdAndActivityTypeAndActivitySourceId(
            Long studentId,
            String activityType,
            Long activitySourceId);

    @EntityGraph(attributePaths = { "lesson" })
    List<StudentActivityProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    long countByStudentIdAndCompletedTrue(Long studentId);
}
