package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.repository.projection.VideoProgressAnalyticsRow;

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

    @Query("""
            select progress.id as id, lesson.id as lessonId,
                   progress.completed as completed, progress.lastWatchedAt as lastWatchedAt,
                   progress.updatedAt as updatedAt
            from VideoProgress progress
            join progress.lesson lesson
            join progress.recordedVideo video
            where progress.student.id = :studentId and progress.id > :afterId
            order by progress.id asc
            """)
    List<VideoProgressAnalyticsRow> findAnalyticsBatch(
            @Param("studentId") Long studentId, @Param("afterId") Long afterId, Pageable pageable);

    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(Long teacherUserId, Long lessonId);

    long countByStudentIdAndRecordedVideoIdInAndCompletedTrue(Long studentId, Collection<Long> recordedVideoIds);
}
