package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    List<AuditLog> findAll();

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    Page<AuditLog> findAll(@Nullable Specification<AuditLog> specification, @NonNull Pageable pageable);
}
