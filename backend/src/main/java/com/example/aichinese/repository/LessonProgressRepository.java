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

import com.example.aichinese.entity.LessonProgress;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link LessonProgress} \u2014 the
 * per-student / per-lesson completion tracker (one row per pair,
 * enforced by a unique constraint on the underlying table) carrying
 * the most recent completion percent, completed flag, and last-accessed
 * timestamp.
 *
 * <p>The repository exposes two ownership-scoped lookup variants
 * differing only in lock mode \u2014 {@link #findByStudentIdAndLessonId}
 * for read-only summary endpoints and
 * {@link #findLockedByStudentIdAndLessonId} for the gamification
 * progress-update flow so two concurrent activity-completion events
 * for the same lesson serialise on the row instead of one of them
 * losing its update.</p>
 *
 * <p>The teacher-scoped {@code existsBy*} predicates traverse
 * {@code lesson.teacher.user} and provide cross-cutting feedback
 * gating: a teacher may only feedback on a learner whose progress
 * touches one of the teacher's lessons (live-class enrolment and
 * quiz-attempt are checked similarly via the sibling repositories).</p>
 */
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    /**
     * Read-only lookup of the (student, lesson) progress row. Returns
     * {@link Optional#empty()} when the learner has not yet touched the
     * lesson; the gamification service lazy-creates the row on first
     * completion via {@link #findLockedByStudentIdAndLessonId}.
     */
    Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    /**
     * Pessimistic-write variant used by the progress-update flow so
     * two concurrent activity-completion events for the same lesson
     * (e.g. quiz submit + drag-drop submit in parallel browser tabs)
     * cannot both compute completion percent against a stale snapshot.
     * The lock is held for the duration of the surrounding
     * {@code @Transactional} method; the eager fetch on
     * {@code student} and {@code lesson} prevents the post-update
     * mapper from triggering an extra round trip.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "lesson" })
    @Query("select lp from LessonProgress lp where lp.student.id = :studentId and lp.lesson.id = :lessonId")
    Optional<LessonProgress> findLockedByStudentIdAndLessonId(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId);

    /**
     * Counts how many distinct lessons a learner has progress on; used
     * by the student dashboard's "lessons started" tile.
     */
    long countByStudentId(Long studentId);

    /**
     * Returns every progress row for a learner ordered by recency \u2014
     * the student dashboard, learning summary, AI context builder and
     * teacher feedback access checks all consume this. Backed by the
     * composite index on {@code (student_id, updated_at)}.
     */
    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    /**
     * Paged variant of {@link #findByStudentIdOrderByUpdatedAtDesc(Long)}
     * for endpoints that page through the learner's lesson history.
     */
    Page<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId, Pageable pageable);

    /**
     * Bulk read used by the teacher-student overview to batch-load every
     * lesson-progress row across the teacher's roster in one query. The
     * eager fetch on {@code student}, {@code student.user},
     * {@code student.currentLevel}, {@code lesson} and
     * {@code lesson.teacher} keeps the rendered roster table off the
     * lazy-loading hot path \u2014 a class of dozens of students would
     * otherwise produce N+1 fan-out per row.
     */
    @EntityGraph(attributePaths = { "student", "student.user", "student.currentLevel", "lesson", "lesson.teacher" })
    List<LessonProgress> findByLessonTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    /**
     * Existence check for the teacher-feedback gating: returns
     * {@code true} only when {@code studentId} has lesson progress on
     * <em>any</em> lesson authored by {@code teacherUserId}.
     */
    boolean existsByStudentIdAndLessonTeacherUserId(Long studentId, Long teacherUserId);

    /**
     * Existence check for the lesson-scoped teacher-feedback gating:
     * returns {@code true} only when {@code studentId} has progress on
     * the specific {@code lessonId} <em>and</em> that lesson is
     * authored by {@code teacherUserId}.
     */
    boolean existsByStudentIdAndLessonIdAndLessonTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);
}
