package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = { "teacher", "teacher.user", "order", "order.student", "order.student.user" })
    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    Optional<OrderItem> findByIdAndOrderStudentUserId(Long id, Long userId);

    boolean existsByOrderIdAndItemTypeAndItemId(Long orderId, String itemType, Long itemId);
}
