package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;
import java.util.Collection;

public interface LessonRepository extends JpaRepository<Lesson, Long> {


    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    Optional<Lesson> findByIdAndTeacherUserId(Long id, Long userId);

    @Override
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    @NonNull
    Optional<Lesson> findById(@NonNull Long id);



    @Query("select l.contentStatus as status, count(l) as count from Lesson l group by l.contentStatus")
    List<StatusCountProjection> groupByContentStatus();

    /** The lessons of the given units students and teachers see (no revision copies), in unit order. */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByModuleIdInAndRevisionOfIsNullOrderByUnitPositionAscCreatedAtAscIdAsc(Collection<Long> moduleIds);

    Optional<Lesson> findFirstByRevisionOfId(Long lessonId);

    List<Lesson> findByRevisionOfIdIn(Collection<Long> lessonIds);

    /** Revisions (working copies) in the given state, oldest submission first. */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByContentStatusAndRevisionOfIsNotNullOrderBySubmittedAtAscIdAsc(ContentStatus contentStatus);

    /** Lessons (never revision copies) in the given state, oldest submission first. */
    @EntityGraph(value = Lesson.GRAPH_DETAIL)
    List<Lesson> findByContentStatusAndRevisionOfIsNullOrderBySubmittedAtAscIdAsc(ContentStatus contentStatus);
}
