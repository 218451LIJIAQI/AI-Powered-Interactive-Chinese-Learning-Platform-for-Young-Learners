package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    List<QuizAttempt> findTop5ByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<QuizAttempt> findTop5ByQuizTeacherIdOrderByCreatedAtDesc(Long teacherId);

    @Query("select avg(qa.score * 1.0) from QuizAttempt qa where qa.quiz.teacher.id = :teacherId and qa.score is not null")
    Double findAverageScoreByTeacherId(@Param("teacherId") Long teacherId);
}
