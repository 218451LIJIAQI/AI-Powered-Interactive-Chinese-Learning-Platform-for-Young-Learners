package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.MatchingGame;

public interface MatchingGameRepository extends JpaRepository<MatchingGame, Long> {

    List<MatchingGame> findByLessonIdIn(Collection<Long> lessonIds);
}
