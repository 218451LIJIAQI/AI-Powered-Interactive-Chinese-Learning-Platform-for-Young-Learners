package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.EmailConfirmationToken;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, Long> {

    @EntityGraph(attributePaths = { "user" })
    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    Optional<EmailConfirmationToken> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** Only the newest link works. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailConfirmationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int retireOpenTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailConfirmationToken t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
