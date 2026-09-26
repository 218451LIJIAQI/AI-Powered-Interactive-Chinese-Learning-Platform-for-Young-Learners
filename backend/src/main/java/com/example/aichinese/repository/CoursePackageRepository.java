package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long>, JpaSpecificationExecutor<CoursePackage> {

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Page<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    Optional<CoursePackage> findById(@NonNull Long id);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByIdIn(List<Long> ids);

    long countByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);

    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    List<CoursePackage> findAll(@Nullable Specification<CoursePackage> specification);

    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    Page<CoursePackage> findAll(@Nullable Specification<CoursePackage> specification, @NonNull Pageable pageable);

    long countByContentStatus(ContentStatus contentStatus);

    long countByTeacherId(Long teacherId);

    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p where p.teacher.id = :teacherId group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatusForTeacher(Long teacherId);

    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
