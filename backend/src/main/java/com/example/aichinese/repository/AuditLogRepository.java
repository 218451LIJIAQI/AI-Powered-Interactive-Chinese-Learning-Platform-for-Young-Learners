package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
