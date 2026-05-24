package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningLevel;

/**
 * Persistence operations for {@link LearningLevel} \u2014 the curated
 * difficulty ladder (e.g. "Beginner", "Intermediate", "Advanced") that
 * lessons, modules and packages are tagged with for filtering and for
 * driving the student's progression milestone awards.
 *
 * <p>The ladder is seeded by {@code DataSeeder} and is effectively
 * read-only at runtime; the public catalogue, the teacher-authoring
 * dropdowns and the student progression engine all render the seeded
 * order via {@link #findAllByOrderBySortOrderAscNameAsc()}.</p>
 */
public interface LearningLevelRepository extends JpaRepository<LearningLevel, Long> {

    /**
     * Looks up a level by its unique business key. Backed by the unique
     * index on the {@code name} column so the lookup stays
     * O(log&nbsp;n). Used by {@code DataSeeder} to make seeding
     * idempotent across deploys.
     */
    Optional<LearningLevel> findByName(String name);

    /**
     * Returns every level in curator-defined display order. The
     * secondary sort by {@code name} is a deterministic tie-breaker so
     * two levels sharing the same {@code sortOrder} still render
     * alphabetically rather than in arbitrary database order.
     */
    List<LearningLevel> findAllByOrderBySortOrderAscNameAsc();
}
