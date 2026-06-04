package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Page<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(Long lessonId, Pageable pageable);

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndLessonIdAndActiveTrue(Long id, Long lessonId);

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndLessonTeacherUserId(Long id, Long teacherUserId);

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    Optional<RecordedLessonVideo> findByIdAndActiveTrue(Long id);

    default List<RecordedLessonVideo> findActiveRelatedToLessonOrPackages(
            Long lessonId,
            @Nullable Collection<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return findActiveRelatedToLesson(lessonId);
        }
        return findActiveRelatedToLessonOrPackagesInternal(lessonId, packageIds);
    }

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    @Query("""
            select v from RecordedLessonVideo v
            where v.active = true
              and v.lesson.id = :lessonId
            order by v.sortOrder asc, v.createdAt asc
            """)
    List<RecordedLessonVideo> findActiveRelatedToLesson(@Param("lessonId") Long lessonId);

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    @Query("""
            select v from RecordedLessonVideo v
            where v.active = true and (
                v.lesson.id = :lessonId
                or v.coursePackage.id in :packageIds
            )
            order by v.sortOrder asc, v.createdAt asc
            """)
    List<RecordedLessonVideo> findActiveRelatedToLessonOrPackagesInternal(
            @Param("lessonId") Long lessonId,
            @Param("packageIds") Collection<Long> packageIds);

    @EntityGraph(value = RecordedLessonVideo.GRAPH_DETAIL)
    @Query(value = """
            select distinct v from RecordedLessonVideo v
            left join v.coursePackage p
            left join PaymentEntitlement e
              on e.student.id = :studentId
             and e.itemType = 'PACKAGE'
             and e.itemId = p.id
             and e.active = true
            where v.active = true
              and v.lesson.id = :lessonId
              and (p is null or p.contentStatus = :published)
              and (p is null or p.accessType <> :paid or e.id is not null)
            order by v.sortOrder asc, v.createdAt asc
            """, countQuery = """
            select count(distinct v.id) from RecordedLessonVideo v
            left join v.coursePackage p
            left join PaymentEntitlement e
              on e.student.id = :studentId
             and e.itemType = 'PACKAGE'
             and e.itemId = p.id
             and e.active = true
            where v.active = true
              and v.lesson.id = :lessonId
              and (p is null or p.contentStatus = :published)
              and (p is null or p.accessType <> :paid or e.id is not null)
            """)
    Page<RecordedLessonVideo> findAccessibleByLessonIdForStudent(
            @Param("lessonId") Long lessonId,
            @Param("studentId") Long studentId,
            @Param("published") ContentStatus published,
            @Param("paid") AccessType paid,
            Pageable pageable);
}
