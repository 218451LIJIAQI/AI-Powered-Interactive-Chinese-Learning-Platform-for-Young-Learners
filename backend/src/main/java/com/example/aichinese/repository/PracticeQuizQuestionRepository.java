package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.PracticeQuizQuestion;

public interface PracticeQuizQuestionRepository extends JpaRepository<PracticeQuizQuestion, Long> {

    List<PracticeQuizQuestion> findByPracticeQuizIdOrderBySortOrderAscIdAsc(Long practiceQuizId);

    @Modifying
    @Transactional
    @Query("delete from PracticeQuizQuestion q where q.practiceQuiz.id = :quizId")
    int deleteByPracticeQuizId(@Param("quizId") Long practiceQuizId);
}
