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
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface LearningModuleRepository extends JpaRepository<LearningModule, Long>, JpaSpecificationExecutor<LearningModule> {

    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Page<LearningModule> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Optional<LearningModule> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    @NonNull
    Optional<LearningModule> findById(@NonNull Long id);

    @Override
    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    @NonNull
    Page<LearningModule> findAll(@Nullable Specification<LearningModule> specification, @NonNull Pageable pageable);

    long countByContentStatus(ContentStatus contentStatus);

    long countByTeacherId(Long teacherId);

    @Query("select m.contentStatus as status, count(m) as count from LearningModule m where m.teacher.id = :teacherId group by m.contentStatus")
    List<StatusCountProjection> groupByContentStatusForTeacher(@Param("teacherId") Long teacherId);

    @Query("select m.contentStatus as status, count(m) as count from LearningModule m group by m.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
