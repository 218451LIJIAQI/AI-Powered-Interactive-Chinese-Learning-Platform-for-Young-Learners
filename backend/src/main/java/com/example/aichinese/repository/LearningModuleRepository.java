package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningModule;

public interface LearningModuleRepository extends JpaRepository<LearningModule, Long> {


    @EntityGraph(value = LearningModule.GRAPH_DETAIL)
    Optional<LearningModule> findByIdAndTeacherUserId(Long id, Long userId);



}
