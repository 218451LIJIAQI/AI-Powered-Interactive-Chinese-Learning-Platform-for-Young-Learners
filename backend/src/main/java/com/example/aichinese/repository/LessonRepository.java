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

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;

public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Page<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findById(Long id);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Optional<Lesson> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByIdIn(List<Long> ids);

    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    List<Lesson> findByModuleIdAndContentStatus(Long moduleId, ContentStatus contentStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    Page<Lesson> findAll(org.springframework.data.jpa.domain.Specification<Lesson> specification, Pageable pageable);

    /**
     * Override of the {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}
     * {@code findAll(Specification)} method so that the public catalog discovery
     * query eagerly fetches the relationships referenced by the response mapper.
     * Without this override the mapper would trigger one lazy SELECT per
     * lesson for teacher/category/level, recreating the N+1 problem we set out
     * to eliminate.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "module", "level", "category" })
    @NonNull
    List<Lesson> findAll(@Nullable org.springframework.data.jpa.domain.Specification<Lesson> specification);

    long countByTeacherId(Long teacherId);

    long countByContentStatus(ContentStatus contentStatus);

    /**
     * One row per distinct {@code contentStatus} for the admin analytics
     * dashboard. Same rationale as
     * {@code LearningModuleRepository.groupByContentStatus()} — replaces a
     * full-table load with a single SQL {@code GROUP BY}.
     */
    @org.springframework.data.jpa.repository.Query(
            "select l.contentStatus as status, count(l) as count from Lesson l group by l.contentStatus")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByContentStatus();
}
