package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;
import com.example.aichinese.entity.enums.ClassRevisionStatus;

public interface CoursePackageRepository extends JpaRepository<CoursePackage, Long> {


    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    Optional<CoursePackage> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    @NonNull
    Optional<CoursePackage> findById(@NonNull Long id);



    @Query("select p.contentStatus as status, count(p) as count from CoursePackage p group by p.contentStatus")
    List<StatusCountProjection> groupByContentStatus();

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByContentStatusOrderBySubmittedAtAscIdAsc(ContentStatus contentStatus);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByRevisionStatusOrderByRevisionSubmittedAtAscIdAsc(ClassRevisionStatus revisionStatus);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByContentStatus(ContentStatus contentStatus);

    @EntityGraph(value = CoursePackage.GRAPH_DETAIL)
    List<CoursePackage> findByTeacherIdAndContentStatus(Long teacherId, ContentStatus contentStatus);
}
