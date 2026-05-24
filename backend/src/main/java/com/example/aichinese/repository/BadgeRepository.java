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

    Optional<Badge> findByName(String name);
}
