package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    List<LessonProgress> findTop5ByStudentIdAndCompletedFalseOrderByUpdatedAtDesc(Long studentId);

    java.util.Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);

    long countByStudentId(Long studentId);

    long countByStudentIdAndCompletedTrue(Long studentId);

    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    @Query("select count(distinct lp.student.id) from LessonProgress lp where lp.lesson.teacher.id = :teacherId")
    long countDistinctStudentsByTeacherId(@Param("teacherId") Long teacherId);
}
