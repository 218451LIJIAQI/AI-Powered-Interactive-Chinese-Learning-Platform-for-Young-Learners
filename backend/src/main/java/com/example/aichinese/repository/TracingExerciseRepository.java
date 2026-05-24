package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.aichinese.entity.TracingExercise;

/**
 * Persistence operations for {@link TracingExercise} \u2014 the platform's
 * character-stroke tracing practice activity.
 *
 * <p>The repository deliberately exposes only the inherited
 * {@link JpaRepository} + {@link JpaSpecificationExecutor} surface so
 * both admin authoring and student library reads share the same
 * dynamic {@link org.springframework.data.jpa.domain.Specification}-
 * based filter API without per-combination derived methods.</p>
 */
public interface TracingExerciseRepository
        extends JpaRepository<TracingExercise, Long>, JpaSpecificationExecutor<TracingExercise> {
}
