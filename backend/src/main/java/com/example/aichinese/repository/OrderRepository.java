package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = { "student", "student.user" })
    List<Order> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "student", "student.user" })
    Optional<Order> findByIdAndStudentUserId(Long id, Long userId);
}
