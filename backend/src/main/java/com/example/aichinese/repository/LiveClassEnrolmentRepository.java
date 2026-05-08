package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.LiveClassEnrolment;

public interface LiveClassEnrolmentRepository extends JpaRepository<LiveClassEnrolment, Long> {
}
