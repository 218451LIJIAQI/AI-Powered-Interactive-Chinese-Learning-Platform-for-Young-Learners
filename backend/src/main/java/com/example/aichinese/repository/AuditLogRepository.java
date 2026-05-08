package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
