package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Badge;

/**
 * Persistence operations for {@link Badge} — the platform-defined
 * achievement tokens awarded to learners (e.g. "First Lesson",
 * "7-Day Streak"). Badges are seeded by {@code DataSeeder} and looked
 * up by {@code name} when the gamification service decides which one
 * to award after a triggering event.
 */
public interface BadgeRepository extends JpaRepository<Badge, Long> {

    /**
     * Looks up a badge by its unique business key. Backed by the unique
     * index {@code idx_badges_name} so the lookup is O(log&nbsp;n).
     *
     * <p>Both {@code DataSeeder} and the gamification service use the
     * {@code findByName(...).orElseGet(() -> save(new Badge(...)))} idiom
     * to make seeding and lazy creation idempotent across deploys; the
     * uniqueness constraint protects against the race where two concurrent
     * callers attempt to create the same badge \u2014 the second one will
     * receive a constraint violation that surfaces as a transactional
     * rollback rather than a duplicate row.</p>
     *
     * @param name canonical badge name (e.g. {@code "First Lesson"})
     * @return the badge when it exists, otherwise {@link Optional#empty()}
     */
    Optional<Badge> findByName(String name);
}
