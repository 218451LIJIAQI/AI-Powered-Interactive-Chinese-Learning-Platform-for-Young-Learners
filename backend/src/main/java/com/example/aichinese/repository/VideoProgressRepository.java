package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.VideoProgress;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link VideoProgress} \u2014 the
 * per-student / per-recorded-video watch-progress row capturing
 * last-watched position, completion flag and last-watched timestamp.
 *
 * <p>Every read eager-fetches {@code student}, {@code student.user},
 * {@code lesson} and {@code recordedVideo} so the teacher progress
 * roster and student playback resume flow render the parent
 * lesson / video metadata in a single round trip. The pessimistic
 * write lock on
 * {@link #findLockedByStudentIdAndRecordedVideoId(Long, Long)} pairs
 * with the unique constraint on {@code (student_id, recorded_video_id)}
 * so two concurrent position updates (e.g. paginated heartbeat plus
 * close-tab beacon) never race the same row.</p>
 */
public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    /**
     * Read-only lookup of a learner's progress on one recorded video.
     * Returns {@link Optional#empty()} the first time the learner
     * opens the video; callers seed a fresh row via
     * {@link #findLockedByStudentIdAndRecordedVideoId(Long, Long)} on
     * the next write.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    Optional<VideoProgress> findByStudentIdAndRecordedVideoId(Long studentId, Long recordedVideoId);

    /**
     * Pessimistic-write variant used by the heartbeat / mark-as-complete
     * flow so two concurrent updates for the same (student, video) pair
     * (e.g. periodic heartbeat plus close-tab beacon) cannot race the
     * same row's last-position / completed-flag write. The lock is
     * held for the duration of the surrounding {@code @Transactional}
     * method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    @Query("select vp from VideoProgress vp where vp.student.id = :studentId and vp.recordedVideo.id = :recordedVideoId")
    Optional<VideoProgress> findLockedByStudentIdAndRecordedVideoId(
            @Param("studentId") Long studentId,
            @Param("recordedVideoId") Long recordedVideoId);

    /**
     * Returns every video-progress row for a learner ordered by recent
     * activity \u2014 used by the analytics service to compute watch
     * patterns. Service-side filtering removes rows that point at
     * videos the learner has lost access to (e.g. soft-deleted by the
     * teacher).
     */
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByStudentIdOrderByLastWatchedAtDesc(Long studentId);

    /**
     * Returns every video-progress row across a teacher's lesson \u2014
     * used by the teacher progress roster on the lesson detail page.
     * Service-side filtering applies accessibility checks before
     * paginating the result.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "lesson", "recordedVideo" })
    List<VideoProgress> findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(Long teacherUserId, Long lessonId);
}
