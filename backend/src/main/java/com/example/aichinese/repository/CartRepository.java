package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
}
