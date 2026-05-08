package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.enums.ContentStatus;

public interface LearningModuleRepository extends JpaRepository<LearningModule, Long>, JpaSpecificationExecutor<LearningModule> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    List<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<LearningModule> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<LearningModule> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<LearningModule> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Page<LearningModule> findAll(org.springframework.data.jpa.domain.Specification<LearningModule> specification, Pageable pageable);

    long countByTeacherId(Long teacherId);

    long countByContentStatus(ContentStatus contentStatus);
}
