package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.repository.projection.LessonProgressAnalyticsRow;

import jakarta.persistence.LockModeType;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "lesson" })
    @Query("select lp from LessonProgress lp where lp.student.id = :studentId and lp.lesson.id = :lessonId")
    Optional<LessonProgress> findLockedByStudentIdAndLessonId(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId);

    @EntityGraph(attributePaths = { "lesson" })
    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    @Query("""
            select progress.id as id, lesson.id as lessonId,
                   lesson.learningType as learningType, progress.completed as completed,
                   progress.lastAccessedAt as lastAccessedAt, progress.updatedAt as updatedAt
            from LessonProgress progress
            join progress.lesson lesson
            where progress.student.id = :studentId and progress.id > :afterId
            order by progress.id asc
            """)
    List<LessonProgressAnalyticsRow> findAnalyticsBatch(
            @Param("studentId") Long studentId, @Param("afterId") Long afterId, Pageable pageable);

    @EntityGraph(attributePaths = { "student", "student.user", "student.currentLevel", "lesson", "lesson.teacher" })
    List<LessonProgress> findByLessonTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    boolean existsByStudentIdAndLessonTeacherUserId(Long studentId, Long teacherUserId);

    boolean existsByStudentIdAndLessonIdAndLessonTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);
}
