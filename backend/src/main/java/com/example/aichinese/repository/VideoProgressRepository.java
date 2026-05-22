package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.VideoProgress;

import jakarta.persistence.LockModeType;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    Optional<VideoProgress> findByStudentIdAndRecordedVideoId(Long studentId, Long recordedVideoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    @Query("select vp from VideoProgress vp where vp.student.id = :studentId and vp.recordedVideo.id = :recordedVideoId")
    Optional<VideoProgress> findLockedByStudentIdAndRecordedVideoId(
            @Param("studentId") Long studentId,
            @Param("recordedVideoId") Long recordedVideoId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByStudentIdOrderByLastWatchedAtDesc(Long studentId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(Long teacherUserId, Long lessonId);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    Page<VideoProgress> findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(Long teacherUserId, Long lessonId, Pageable pageable);
}
