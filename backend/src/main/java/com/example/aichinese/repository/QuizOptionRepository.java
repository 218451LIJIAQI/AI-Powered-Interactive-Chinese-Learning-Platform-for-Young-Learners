package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.QuizOption;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    List<QuizOption> findByQuestionIdOrderBySortOrderAscCreatedAtAsc(Long questionId);

    void deleteByQuestionId(Long questionId);
}
