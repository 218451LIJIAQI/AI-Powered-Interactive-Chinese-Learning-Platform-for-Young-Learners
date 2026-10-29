package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Quiz;
import java.util.Collection;
import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {



    List<Quiz> findByLessonIdOrderByCreatedAtAscIdAsc(Long lessonId);

    @EntityGraph(attributePaths = { "lesson" })
    List<Quiz> findByLessonIdIn(Collection<Long> lessonIds);
}
