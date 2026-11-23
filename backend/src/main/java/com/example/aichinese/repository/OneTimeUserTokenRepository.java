package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.base.OneTimeUserToken;

/** Shared atomic lookup, retirement and consumption rules for single-use user link tokens. */
@NoRepositoryBean
public interface OneTimeUserTokenRepository<T extends OneTimeUserToken> extends JpaRepository<T, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<T> findByTokenHash(String tokenHash);

    /**
     * Atomically claims an unused, non-expired token. Deliberately omits {@code clearAutomatically} so callers
     * continue mutating their managed {@code User} entity in the same transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query("update #{#entityName} t set t.usedAt = :now, t.updatedAt = :now "
            + "where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int consumeActiveToken(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Retires every open link of the user without clearing the persistence context, keeping the caller's managed
     * write-locked {@code User} attached for subsequent updates.
     */
    @Modifying(flushAutomatically = true)
    @Query("update #{#entityName} t set t.usedAt = :now, t.updatedAt = :now "
            + "where t.user.id = :userId and t.usedAt is null")
    int retireOpenTokensForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
