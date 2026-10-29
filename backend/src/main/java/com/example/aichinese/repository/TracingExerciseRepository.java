package com.example.aichinese.repository;

import com.example.aichinese.entity.TracingExercise;
import java.util.Collection;
import java.util.List;

public interface TracingExerciseRepository extends PracticeActivityRepository<TracingExercise> {

    /** The activities of a class lesson, in lesson order. */
    List<TracingExercise> findByLessonIdOrderByLessonPositionAscIdAsc(Long lessonId);

    List<TracingExercise> findByLessonIdIn(Collection<Long> lessonIds);
}
