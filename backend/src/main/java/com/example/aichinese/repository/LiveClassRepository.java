package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LiveClassStatus;

import jakarta.persistence.LockModeType;

public interface LiveClassRepository extends JpaRepository<LiveClass, Long> {

    long countByTeacherIdAndScheduledStartAtAfterAndLiveClassStatusNotIn(
            Long teacherId,
            LocalDateTime now,
            Collection<LiveClassStatus> excludedStatuses);

    @Override
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @NonNull
    Optional<LiveClass> findById(@NonNull Long id);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Optional<LiveClass> findByIdAndTeacherUserId(Long id, Long teacherUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query("select lc from LiveClass lc where lc.id = :id")
    Optional<LiveClass> findLockedById(@Param("id") Long id);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    List<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Page<LiveClass> findByTeacherUserIdOrderByScheduledStartAtAsc(Long teacherUserId, Pageable pageable);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    Page<LiveClass> findAllByOrderByScheduledStartAtAsc(Pageable pageable);

    default List<LiveClass> findRelatedToLessonOrPackages(
            Long lessonId,
            @Nullable Collection<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return findRelatedToLesson(lessonId);
        }
        return findRelatedToLessonOrPackagesInternal(lessonId, packageIds);
    }

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query("""
            select lc from LiveClass lc
            where lc.lesson.id = :lessonId
            """)
    List<LiveClass> findRelatedToLesson(@Param("lessonId") Long lessonId);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query("""
            select lc from LiveClass lc
            where lc.lesson.id = :lessonId
               or lc.coursePackage.id in :packageIds
            """)
    List<LiveClass> findRelatedToLessonOrPackagesInternal(
            @Param("lessonId") Long lessonId,
            @Param("packageIds") Collection<Long> packageIds);

    @EntityGraph(value = LiveClass.GRAPH_DETAIL)
    @Query(value = """
            select lc from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
              and lc.liveClassStatus in :visibleStatuses
            order by lc.scheduledStartAt asc
            """, countQuery = """
            select count(lc) from LiveClass lc
            left join lc.lesson l
            left join lc.coursePackage p
            where (l is not null or p is not null)
              and (l is null or l.contentStatus = :published)
              and (p is null or p.contentStatus = :published)
              and lc.liveClassStatus in :visibleStatuses
            """)
    Page<LiveClass> findStudentVisibleClasses(
            @Param("published") ContentStatus published,
            @Param("visibleStatuses") Collection<LiveClassStatus> visibleStatuses,
            Pageable pageable);
}
