package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentQuizProgress;

public interface StudentQuizProgressRepository extends JpaRepository<StudentQuizProgress, Long> {

    @EntityGraph(attributePaths = { "lesson" })
    List<StudentQuizProgress> findByStudentIdAndLessonIdOrderByUpdatedAtDesc(Long studentId, Long lessonId);

    @EntityGraph(attributePaths = { "lesson" })
    Optional<StudentQuizProgress> findByStudentIdAndQuizId(Long studentId, Long quizId);
}
