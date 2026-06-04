package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.TracingExercise;

public interface TracingExerciseRepository
        extends JpaRepository<TracingExercise, Long>, JpaSpecificationExecutor<TracingExercise> {
}
