package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.AiUsageLog;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {

    long countByStatusIgnoreCase(String status);

    @Override
    @EntityGraph(attributePaths = "user")
    org.springframework.data.domain.Page<AiUsageLog> findAll(
            org.springframework.data.jpa.domain.Specification<AiUsageLog> specification,
            org.springframework.data.domain.Pageable pageable);

    @Query("select count(a) from AiUsageLog a where lower(a.status) = 'success'")
    long countSuccessfulRequests();
}
