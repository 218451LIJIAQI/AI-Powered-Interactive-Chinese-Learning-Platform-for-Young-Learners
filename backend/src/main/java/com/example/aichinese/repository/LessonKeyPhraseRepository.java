package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LessonKeyPhrase;

public interface LessonKeyPhraseRepository extends JpaRepository<LessonKeyPhrase, Long> {

    List<LessonKeyPhrase> findByLessonIdOrderBySortOrderAscIdAsc(Long lessonId);
}
