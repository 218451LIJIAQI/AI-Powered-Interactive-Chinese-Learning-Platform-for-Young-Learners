package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
