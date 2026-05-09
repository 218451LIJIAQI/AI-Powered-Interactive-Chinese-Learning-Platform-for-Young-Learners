package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.VideoProgress;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    Optional<VideoProgress> findByStudentIdAndRecordedVideoId(Long studentId, Long recordedVideoId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByStudentIdOrderByLastWatchedAtDesc(Long studentId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByStudentIdAndCompletedFalseOrderByLastWatchedAtDesc(Long studentId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(Long teacherUserId, Long lessonId);
}
