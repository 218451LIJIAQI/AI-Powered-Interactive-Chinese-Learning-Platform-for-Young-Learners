package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

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

    /**
     * Specification-based override that eagerly fetches teacher/level/category
     * so that the public catalog mapper does not trigger one lazy SELECT per
     * row when assembling discovery responses.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "level", "category" })
    @NonNull
    List<CoursePackage> findAll(@Nullable org.springframework.data.jpa.domain.Specification<CoursePackage> specification);

    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * dashboard. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()}.
     */
    @org.springframework.data.jpa.repository.Query(
            "select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByContentStatus();
}
