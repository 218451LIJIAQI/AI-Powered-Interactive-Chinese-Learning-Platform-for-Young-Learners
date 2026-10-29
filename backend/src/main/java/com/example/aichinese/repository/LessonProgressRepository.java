package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByStudentIdAndLessonId(Long studentId, Long lessonId);


    @EntityGraph(attributePaths = { "lesson" })
    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

}
