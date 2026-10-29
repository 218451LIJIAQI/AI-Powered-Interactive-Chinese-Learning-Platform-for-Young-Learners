package com.example.aichinese.repository;

import com.example.aichinese.entity.MatchingGame;
import java.util.Collection;
import java.util.List;

public interface MatchingGameRepository extends PracticeActivityRepository<MatchingGame> {

    /** The activities of a class lesson, in lesson order. */
    List<MatchingGame> findByLessonIdOrderByLessonPositionAscIdAsc(Long lessonId);

    List<MatchingGame> findByLessonIdIn(Collection<Long> lessonIds);
}
