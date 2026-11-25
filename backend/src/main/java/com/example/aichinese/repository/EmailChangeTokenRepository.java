package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.EmailChangeToken;

import jakarta.persistence.LockModeType;

public interface EmailChangeTokenRepository extends OneTimeUserTokenRepository<EmailChangeToken> {

    String LATEST_PENDING_BY_USER = """
            from EmailChangeToken t
            where t.user.id = :userId and t.usedAt is null and t.expiresAt > :now
            order by t.createdAt desc, t.id desc
            limit 1
            """;

    @Query("select t.newEmail " + LATEST_PENDING_BY_USER)
    Optional<String> findPendingEmail(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Locking read sees concurrent cancellations committed while waiting for the account lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t " + LATEST_PENDING_BY_USER)
    Optional<EmailChangeToken> findLockedPendingToken(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
