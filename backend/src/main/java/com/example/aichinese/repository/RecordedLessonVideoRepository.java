package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RecordedLessonVideo;

public interface RecordedLessonVideoRepository extends JpaRepository<RecordedLessonVideo, Long> {

    Optional<RecordedLessonVideo> findFirstByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(Long lessonId);

    List<RecordedLessonVideo> findByLessonIdInAndActiveTrueOrderBySortOrderAscIdAsc(Collection<Long> lessonIds);

    @EntityGraph(attributePaths = "uploadedFile")
    List<RecordedLessonVideo> findByLessonId(Long lessonId);
}
