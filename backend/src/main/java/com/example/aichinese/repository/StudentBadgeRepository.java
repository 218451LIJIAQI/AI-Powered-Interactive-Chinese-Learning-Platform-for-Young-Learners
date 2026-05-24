package com.example.aichinese.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.StudentBadge;

/**
 * Persistence operations for {@link StudentBadge} \u2014 the join row
 * that records "{@code studentId} has earned {@code badgeId}", with
 * the {@code awardedAt} timestamp displayed in the rewards card.
 *
 * <p>The unique constraint on {@code (student_id, badge_id)} guarantees
 * a learner can earn each badge at most once; the gamification engine
 * pre-checks with
 * {@link #existsByStudentIdAndBadgeId(Long, Long)} before inserting so
 * the user-facing flow surfaces a no-op rather than a constraint
 * violation, while the constraint itself remains the race-safe boundary.</p>
 */
public interface StudentBadgeRepository extends JpaRepository<StudentBadge, Long> {

    /**
     * Counts every badge a learner has earned \u2014 backs the student
     * dashboard's "badges earned" tile.
     */
    long countByStudentId(Long studentId);

    /**
     * Idempotency probe for the gamification engine: returns {@code true}
     * if {@code studentId} has already earned {@code badgeId}, letting
     * the engine skip the award and avoid a constraint violation.
     */
    boolean existsByStudentIdAndBadgeId(Long studentId, Long badgeId);

    /**
     * Paged badges-earned history backing the student "my rewards"
     * page; orders by {@code awardedAt} desc so the most recently
     * earned badge surfaces first.
     */
    @EntityGraph(attributePaths = { "badge" })
    Page<StudentBadge> findByStudentIdOrderByAwardedAtDesc(Long studentId, Pageable pageable);
}
