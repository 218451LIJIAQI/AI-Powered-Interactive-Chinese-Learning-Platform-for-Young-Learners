package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LearningCategory;

/**
 * Persistence operations for {@link LearningCategory} \u2014 the curated
 * topic taxonomy (e.g. "Greetings", "Numbers", "Family") that lessons,
 * modules and packages are tagged with for filtering and discovery.
 *
 * <p>The taxonomy is seeded by {@code DataSeeder} and is effectively
 * read-only at runtime; the public catalogue and the teacher-authoring
 * dropdowns both render the seeded order via
 * {@link #findAllByOrderBySortOrderAscNameAsc()}.</p>
 */
public interface LearningCategoryRepository extends JpaRepository<LearningCategory, Long> {

    /**
     * Looks up a category by its unique business key. Backed by the
     * unique index {@code idx_learning_categories_name} so the lookup
     * stays O(log&nbsp;n). Used by {@code DataSeeder} to make seeding
     * idempotent across deploys: the
     * {@code findByName(...).orElseGet(() -> save(new ...))} idiom
     * relies on the uniqueness to make the constraint, rather than a
     * pre-write check, the race-safe enforcement boundary.
     */
    Optional<LearningCategory> findByName(String name);

    /**
     * Returns every category in the curator-defined display order.
     * Sorts primarily by {@code sortOrder} (the explicit position the
     * curator assigns) with {@code name} as a deterministic tie-breaker
     * so two categories sharing the same sort order still render
     * alphabetically rather than in arbitrary database order.
     */
    List<LearningCategory> findAllByOrderBySortOrderAscNameAsc();
}
