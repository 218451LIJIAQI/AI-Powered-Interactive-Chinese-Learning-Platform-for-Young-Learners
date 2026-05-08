package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
