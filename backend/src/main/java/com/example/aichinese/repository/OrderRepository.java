package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.Order;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link Order} \u2014 the checkout aggregate
 * that groups one or more {@code OrderItem}s into a single transaction
 * and carries the buyer ({@code student}) and the gross-amount /
 * payment-status header.
 *
 * <p>All reads eager-fetch {@code student} and {@code student.user} so
 * the receipt and admin order-detail mappers can render the buyer name
 * without an extra round trip. The repository exposes both a
 * read-only and a pessimistic-write IDOR-safe lookup so the
 * checkout / refund flows can serialise concurrent state changes on
 * the same order without contending on the read paths.</p>
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Returns every order placed by {@code userId} ordered newest-first
     * for the student "my orders" page. Backed by the implicit composite
     * index on {@code (student.user_id, created_at)}.
     */
    @EntityGraph(attributePaths = { "student", "student.user" })
    List<Order> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code Order \u2192 StudentProfile \u2192 User} so a buyer cannot
     * surface another buyer's order by guessing its id. The service
     * layer maps an empty {@link Optional} to a 404 to avoid disclosing
     * cross-user order ids.
     */
    @EntityGraph(attributePaths = { "student", "student.user" })
    Optional<Order> findByIdAndStudentUserId(Long id, Long userId);

    /**
     * Pessimistic-write variant used by the checkout / refund flows so
     * two concurrent state-change requests for the same order (e.g. the
     * Stripe webhook arriving while the user clicks "Cancel") cannot
     * race the same status transition. The lock is held for the
     * duration of the surrounding {@code @Transactional} method; the
     * explicit JPQL avoids the {@code SELECT \u2026 FOR UPDATE}
     * side-effect that would otherwise leak into the read-only path
     * if the {@code @Lock} annotation were placed on the derived
     * read method.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "student", "student.user" })
    @Query("select o from Order o where o.id = :id and o.student.user.id = :userId")
    Optional<Order> findLockedByIdAndStudentUserId(@Param("id") Long id, @Param("userId") Long userId);
}
