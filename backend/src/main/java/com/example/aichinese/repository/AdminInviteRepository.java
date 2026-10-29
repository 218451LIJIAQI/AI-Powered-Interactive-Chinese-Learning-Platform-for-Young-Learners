package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AdminInvite;

public interface AdminInviteRepository extends JpaRepository<AdminInvite, Long> {

    Optional<AdminInvite> findByTokenHash(String tokenHash);

    /** The invite for this address that has not been accepted yet (expired or not); at most one exists. */
    Optional<AdminInvite> findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(String email);

    /** Invites that can still be accepted, newest first. */
    @Query("select i from AdminInvite i where i.acceptedAt is null and i.expiresAt > :now order by i.createdAt desc")
    List<AdminInvite> findPending(@Param("now") LocalDateTime now);
}
