package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RecordedLessonVideo;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    /**
     * A batch of videos with everything ContentVisibilityPolicy.isRecordedVideoVisible reads (lesson, module,
     * course and their teachers' accounts), in one query. Callers pass a non-empty set.
     */
    @EntityGraph(attributePaths = {
            "lesson", "lesson.module", "lesson.teacher", "lesson.teacher.user",
            "coursePackage", "coursePackage.teacher", "coursePackage.teacher.user"
    })
    List<RecordedLessonVideo> findByIdIn(Collection<Long> ids);

    List<RecordedLessonVideo> findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(Long lessonId);

    List<RecordedLessonVideo> findByLessonIdInAndActiveTrue(Collection<Long> lessonIds);

    List<RecordedLessonVideo> findByLessonId(Long lessonId);
}
