package com.example.aichinese.repository;

import com.example.aichinese.entity.Flashcard;
import java.util.Collection;
import java.util.List;

public interface FlashcardRepository extends PracticeActivityRepository<Flashcard> {

    /** The activities of a class lesson, in lesson order. */
    List<Flashcard> findByLessonIdOrderByLessonPositionAscIdAsc(Long lessonId);

    List<Flashcard> findByLessonIdIn(Collection<Long> lessonIds);
}
