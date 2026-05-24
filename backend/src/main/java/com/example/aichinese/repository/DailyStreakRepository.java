package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.DailyStreak;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link DailyStreak} — the consecutive-day
 * learning streak counter for each {@code StudentProfile}.
 *
 * <p>The underlying table enforces exactly zero or one row per student
 * via the {@code uk_daily_streaks_student} unique constraint, so both
 * lookups below are O(1) index seeks.</p>
 *
 * <p>Concurrency is layered: the entity declares a {@code @Version}
 * column for optimistic locking that catches "two browser tabs of the
 * same learner" races at commit, and the {@link #findLockedByStudentId}
 * variant adds a pessimistic write lock for the gamification
 * touch-streak hot path so concurrent learning events serialise on the
 * row instead of one of them retrying after an
 * {@link jakarta.persistence.OptimisticLockException}. The plain
 * {@link #findByStudentId} read-only variant skips the lock so summary
 * endpoints (dashboard, reward overview) do not contend with the write
 * path.</p>
 */
public interface DailyStreakRepository extends JpaRepository<DailyStreak, Long> {

    /**
     * Read-only ownership-scoped lookup. Returns {@link Optional#empty()}
     * for a learner who has not yet recorded any learning activity (the
     * streak row is created lazily by the gamification service on the
     * first {@code touchDailyStreak} call).
     */
    Optional<DailyStreak> findByStudentId(Long studentId);

    /**
     * Pessimistic-write variant used by the gamification
     * {@code touchDailyStreak} flow. The lock is held for the duration
     * of the surrounding {@code @Transactional} method so two concurrent
     * learning events for the same student (quiz submit + lesson
     * complete in parallel browser tabs) cannot double-increment the
     * streak.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ds from DailyStreak ds where ds.student.id = :studentId")
    Optional<DailyStreak> findLockedByStudentId(@Param("studentId") Long studentId);
}
