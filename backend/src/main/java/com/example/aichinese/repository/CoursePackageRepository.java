package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.enums.ContentStatus;

public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long>, JpaSpecificationExecutor<CoursePackage> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    List<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Optional<CoursePackage> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    Page<CoursePackage> findAll(org.springframework.data.jpa.domain.Specification<CoursePackage> specification, Pageable pageable);

    long countByContentStatus(ContentStatus contentStatus);
}
