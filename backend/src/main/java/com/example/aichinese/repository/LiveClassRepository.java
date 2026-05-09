package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClass;

public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findAll();

    @Override
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Optional<LiveClass> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "lesson", "lesson.teacher", "coursePackage", "coursePackage.teacher" })
    List<LiveClass> findByScheduledStartAtBetweenOrderByScheduledStartAtAsc(LocalDateTime dateFrom, LocalDateTime dateTo);
}
