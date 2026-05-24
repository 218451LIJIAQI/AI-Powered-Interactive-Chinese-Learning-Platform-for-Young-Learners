package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Cart;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for the per-learner shopping {@link Cart}.
 *
 * <p>Each {@code StudentProfile} owns at most one cart row; the cart
 * service relies on the {@code (student_id)} uniqueness on the
 * underlying table to enforce that invariant. Both lookups eagerly
 * fetch {@code student} and {@code student.user} so the cart service
 * can render owner metadata and check ownership without an extra
 * round-trip per request.</p>
 *
 * <p>The repository deliberately exposes two ownership-scoped lookups
 * differing only in lock mode \u2014 {@link #findByStudentUserId(Long)}
 * for read-only flows (display, checkout summary) and
 * {@link #findLockedByStudentUserId(Long)} for write flows (add-item)
 * so the service can serialise concurrent add-item requests for the
 * same learner and avoid double-inserting the same artefact under the
 * unique-index race window of {@code CartItemRepository}.</p>
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Read-only ownership-scoped cart lookup. Returns
     * {@link Optional#empty()} when the learner has not yet seeded a
     * cart row \u2014 callers (the cart service) typically lazy-create
     * one via {@code save(new Cart(...))} on first add.
     */
    @EntityGraph(attributePaths = { "student", "student.user" })
    Optional<Cart> findByStudentUserId(Long userId);

    /**
     * Pessimistic-write variant used by the add-item flow so two
     * concurrent {@code POST /cart/items} calls for the same learner
     * cannot both pass the in-flight duplicate check before either has
     * committed. The lock is held for the duration of the surrounding
     * {@code @Transactional} method; the explicit JPQL avoids the
     * {@code SELECT \u2026 FOR UPDATE} side-effect that would otherwise
     * leak into the read-only path if the {@code @Lock} annotation were
     * placed on the derived method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user" })
    @Query("select c from Cart c where c.student.user.id = :userId")
    Optional<Cart> findLockedByStudentUserId(@Param("userId") Long userId);
}
