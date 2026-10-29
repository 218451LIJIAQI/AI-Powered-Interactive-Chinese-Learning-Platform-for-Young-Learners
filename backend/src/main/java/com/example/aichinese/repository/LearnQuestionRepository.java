package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface LearnQuestionRepository extends JpaRepository<LearnQuestion, Long> {

    List<LearnQuestion> findByLessonIdOrderByPositionAsc(Long lessonId);

    Optional<LearnQuestion> findByIdAndLessonId(Long id, Long lessonId);

    /** Questions per stop (admin unit drawer). */
    @Query("select q.lesson.id as id, count(q) as total from LearnQuestion q group by q.lesson.id")
    List<IdCountProjection> countPerLesson();
}
