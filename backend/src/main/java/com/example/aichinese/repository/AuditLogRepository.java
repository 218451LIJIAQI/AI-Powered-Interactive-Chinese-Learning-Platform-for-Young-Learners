package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findAll();
}
