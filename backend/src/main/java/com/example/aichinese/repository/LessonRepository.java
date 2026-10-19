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

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Page<Lesson> findByTeacherUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    @NonNull
    Optional<Lesson> findById(@NonNull Long id);

    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndContentStatus(Long id, ContentStatus contentStatus);

    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndContentStatusAndTeacherTeacherStatusAndTeacherPublicVisibleTrue(
            Long id,
            ContentStatus contentStatus,
            TeacherStatus teacherStatus);

    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByIdIn(List<Long> ids);

    /** Published lessons a visitor can find: the lesson and its module (if any) are both published. */
    @Query("select count(l) from Lesson l left join l.module m where l.teacher.id = :teacherId"
            + " and l.contentStatus = :status and (m is null or m.contentStatus = :status)")
    long countPublicLessonsByTeacherId(@Param("teacherId") Long teacherId, @Param("status") ContentStatus status);

    @Override
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    @NonNull
    Page<Lesson> findAll(@Nullable Specification<Lesson> specification, @NonNull Pageable pageable);

    long countByTeacherId(Long teacherId);

    @Query("select l.contentStatus as status, count(l) as count from Lesson l where l.teacher.id = :teacherId group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatusForTeacher(@Param("teacherId") Long teacherId);

    @Query("select l.contentStatus as status, count(l) as count from Lesson l group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatus();
}
