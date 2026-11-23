package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.repository.projection.IdCountProjection;

public interface LearnQuestionRepository extends JpaRepository<LearnQuestion, Long> {

    List<LearnQuestion> findByLessonIdOrderByPositionAscIdAsc(Long lessonId);

    List<HomeView.Phrase> findPhrasesByLessonIdOrderByPositionAscIdAsc(Long lessonId);

    Optional<LearnQuestion> findByIdAndLessonId(Long id, Long lessonId);

    /** The student path shows how many questions each level checkpoint asks. */
    @Query("""
            select q.lesson.id as id, count(q) as total from LearnQuestion q
            where q.lesson.lessonKind = com.example.aichinese.entity.enums.LearnLessonKind.CHECKPOINT
            group by q.lesson.id
            """)
    List<IdCountProjection> countPerCheckpoint();
}
