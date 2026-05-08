package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AiUsageLog;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    long countByStatusIgnoreCase(String status);
}
