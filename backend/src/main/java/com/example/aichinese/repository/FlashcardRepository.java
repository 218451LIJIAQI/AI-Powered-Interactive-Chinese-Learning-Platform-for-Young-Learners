package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByLessonIdIn(Collection<Long> lessonIds);
}
