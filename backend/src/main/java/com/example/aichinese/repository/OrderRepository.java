package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);
}
