package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.OrderItem;

/**
 * Persistence operations for {@link OrderItem} \u2014 the line items of a
 * checkout {@code Order}, each capturing one purchasable artefact (lesson
 * / package / live class) along with the teacher who authored it for the
 * commission split.
 *
 * <p>Both reads eager-fetch teacher / teacher.user / order /
 * order.student / order.student.user so the order summary, payment
 * receipt and earning-split mappers can render the buyer + seller
 * metadata in a single round trip.</p>
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Returns every line of {@code orderId} in insertion order so the
     * receipt UI preserves the chronological "added first at the top"
     * convention. Backed by the {@code order_id} foreign-key index.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "order", "order.student", "order.student.user" })
    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    /**
     * Bulk hydration of every line across multiple orders, used by the
     * student "my payments" page to render line items inline with each
     * payment row without a per-order round trip. Sorts by
     * {@code order_id} first so the result groups naturally per parent
     * order, and by {@code created_at} within each order for the same
     * reason as the single-order variant.
     */
    @EntityGraph(attributePaths = { "teacher", "teacher.user", "order", "order.student", "order.student.user" })
    List<OrderItem> findByOrderIdInOrderByOrderIdAscCreatedAtAsc(Collection<Long> orderIds);
}
