package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiUsageQuotaCounter;

import jakarta.persistence.LockModeType;

public interface AiUsageQuotaCounterRepository extends JpaRepository<AiUsageQuotaCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select counter from AiUsageQuotaCounter counter where counter.user.id = :userId")
    Optional<AiUsageQuotaCounter> findLockedByUserId(@Param("userId") Long userId);
}
