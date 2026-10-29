package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.EmailChangeToken;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {

    @EntityGraph(attributePaths = { "user" })
    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    /** The newest change link of the user that can still be opened (shown as "waiting for confirmation"). */
    Optional<EmailChangeToken> findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, LocalDateTime now);

    /** Retires every open link of the user, so only the newest one works. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailChangeToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int retireOpenTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailChangeToken t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
