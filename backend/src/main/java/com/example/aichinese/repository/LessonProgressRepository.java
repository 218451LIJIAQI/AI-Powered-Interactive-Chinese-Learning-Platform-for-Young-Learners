package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LessonProgress;

import jakarta.persistence.LockModeType;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    List<LessonProgress> findTop5ByStudentIdAndCompletedFalseOrderByUpdatedAtDesc(Long studentId);

    java.util.Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "lesson" })
    @Query("select lp from LessonProgress lp where lp.student.id = :studentId and lp.lesson.id = :lessonId")
    java.util.Optional<LessonProgress> findLockedByStudentIdAndLessonId(
            @Param("studentId") Long studentId,
            @Param("lessonId") Long lessonId);

    long countByStudentId(Long studentId);

    long countByStudentIdAndCompletedTrue(Long studentId);

    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    Page<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "student", "student.user", "student.currentLevel", "lesson", "lesson.teacher" })
    List<LessonProgress> findByLessonTeacherIdAndStudentIdIn(Long teacherId, List<Long> studentIds);

    boolean existsByStudentIdAndLessonTeacherUserId(Long studentId, Long teacherUserId);

    boolean existsByStudentIdAndLessonIdAndLessonTeacherUserId(Long studentId, Long lessonId, Long teacherUserId);

    @Query("select count(distinct lp.student.id) from LessonProgress lp where lp.lesson.teacher.id = :teacherId")
    long countDistinctStudentsByTeacherId(@Param("teacherId") Long teacherId);
}
