package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;

import com.example.aichinese.entity.EmailConfirmationToken;

import jakarta.persistence.LockModeType;

public interface EmailConfirmationTokenRepository extends OneTimeUserTokenRepository<EmailConfirmationToken> {

    /** Locking read ensures the latest committed send history is seen even under a joined REPEATABLE READ snapshot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailConfirmationToken> findByUserIdAndCreatedAtAfterOrderByCreatedAtDescIdDesc(Long userId, LocalDateTime since);
}
