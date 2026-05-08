package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;

public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Page<Lesson> findAll(org.springframework.data.jpa.domain.Specification<Lesson> specification, Pageable pageable);

    long countByTeacherId(Long teacherId);

    long countByContentStatus(ContentStatus contentStatus);
}
