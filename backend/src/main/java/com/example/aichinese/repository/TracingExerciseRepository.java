package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.TracingExercise;

public interface TracingExerciseRepository extends JpaRepository<TracingExercise, Long> {

    List<TracingExercise> findByLessonIdIn(Collection<Long> lessonIds);
}
