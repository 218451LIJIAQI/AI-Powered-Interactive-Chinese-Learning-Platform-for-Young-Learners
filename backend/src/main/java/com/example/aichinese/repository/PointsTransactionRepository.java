package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PointsTransaction;

/**
 * Persistence operations for {@link PointsTransaction} \u2014 the
 * append-only ledger of every gamification points award (lesson
 * completion, quiz pass, streak milestone, etc.).
 *
 * <p>Writes are guarded by
 * {@link #existsByStudentIdAndSourceTypeAndSourceId(Long, String, Long)}
 * inside
 * {@code StudentLearningProgressService.awardPointsIfFirst}; that
 * idempotency check coupled with the unique constraint on
 * {@code (student_id, source_type, source_id)} guarantees a learner is
 * never awarded points twice for the same achievement, even under
 * concurrent submission races.</p>
 */
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    /**
     * Unbounded transaction list for the integration tests that assert
     * specific gamification award sequences. Production reads paginate
     * via {@link #findByStudentIdOrderByCreatedAtDesc(Long, Pageable)}.
     */
    List<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /**
     * Paged points history backing the student "my rewards" page and
     * the recent-points strip on the reward summary card.
     */
    Page<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    /**
     * Idempotency probe used by
     * {@code StudentLearningProgressService.awardPointsIfFirst} so the
     * gamification engine never double-credits a learner who replays
     * the same activity. Backed by the unique constraint on
     * {@code (student_id, source_type, source_id)} which makes the
     * check race-safe \u2014 a concurrent re-award attempt either sees
     * the existing row here or surfaces a
     * {@code DataIntegrityViolationException} on insert.
     */
    boolean existsByStudentIdAndSourceTypeAndSourceId(Long studentId, String sourceType, Long sourceId);
}
