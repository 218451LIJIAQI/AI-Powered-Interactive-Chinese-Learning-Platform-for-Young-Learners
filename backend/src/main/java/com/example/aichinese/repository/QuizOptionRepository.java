package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.QuizOption;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    List<QuizOption> findByQuestionIdOrderBySortOrderAscCreatedAtAsc(Long questionId);

    @Modifying
    @Transactional
    @Query("delete from QuizOption o where o.question.id = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
