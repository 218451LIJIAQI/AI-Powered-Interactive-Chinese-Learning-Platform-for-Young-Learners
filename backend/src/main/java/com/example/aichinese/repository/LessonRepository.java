package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Override
    @EntityGraph(attributePaths = "module")
    @NonNull
    Optional<Lesson> findById(@NonNull Long id);

    /** Lessons per status for the admin analytics; working copies are changes to a lesson, not lessons. */
    @Query("select l.contentStatus as status, count(l) as count from Lesson l where l.revisionOf is null"
            + " group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatus();

    /** The lessons of the given units (no working copies), in unit order. */
    List<Lesson> findByModuleIdInAndRevisionOfIsNullOrderByUnitPositionAscCreatedAtAscIdAsc(Collection<Long> moduleIds);

    @Query("select coalesce(max(l.unitPosition), 0) from Lesson l where l.module.id = :unitId and l.revisionOf is null")
    int lastPositionInUnit(@Param("unitId") Long unitId);

    Optional<Lesson> findFirstByRevisionOfId(Long lessonId);

    /** Demo histories refer to the original lesson in a named class. Lesson titles can repeat across classes. */
    @Query("""
            select lesson from Lesson lesson
            join PackageItem unit on unit.module = lesson.module
            where unit.coursePackage.title = :classTitle and lesson.title = :lessonTitle and lesson.revisionOf is null
            order by lesson.id limit 1
            """)
    Optional<Lesson> findByClassAndLessonTitle(@Param("classTitle") String classTitle, @Param("lessonTitle") String lessonTitle);

    @EntityGraph(attributePaths = "revisionOf")
    List<Lesson> findByRevisionOfIdIn(Collection<Long> lessonIds);

    /** Scalar owner class id(s) of a non-revision lesson, avoiding loading managed entities before taking the class lock. */
    @Query("""
            select distinct item.coursePackage.id from Lesson lesson
            join PackageItem item on item.module = lesson.module
            where lesson.id = :lessonId and lesson.revisionOf is null
            order by item.coursePackage.id
            """)
    List<Long> findClassIdsOfLesson(@Param("lessonId") Long lessonId);
}
