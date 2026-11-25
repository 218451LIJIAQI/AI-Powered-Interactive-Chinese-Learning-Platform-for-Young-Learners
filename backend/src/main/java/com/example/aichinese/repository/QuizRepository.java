package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    boolean existsByLessonId(Long lessonId);

    List<Quiz> findByLessonIdInOrderByCreatedAtAscIdAsc(Collection<Long> lessonIds);
}
