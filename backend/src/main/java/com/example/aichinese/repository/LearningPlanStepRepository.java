package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningPlanStep;

public interface LearningPlanStepRepository extends JpaRepository<LearningPlanStep, Long> {

    List<LearningPlanStep> findByPlanIdOrderByPositionAsc(Long planId);
}
