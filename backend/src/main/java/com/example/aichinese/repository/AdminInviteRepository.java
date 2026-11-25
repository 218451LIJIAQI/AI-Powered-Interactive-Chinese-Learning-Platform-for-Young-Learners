package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AdminInvite;

import jakarta.persistence.LockModeType;

public interface AdminInviteRepository extends JpaRepository<AdminInvite, Long> {

    Optional<AdminInvite> findByTokenHash(String tokenHash);

    /**
     * The latest invite for this address that has not been accepted yet (expired or cancelled too); locked directly
     * with PESSIMISTIC_WRITE to avoid S-to-X lock upgrade deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdminInvite> findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(String email);

    /** Invites that can still be accepted, the most recently sent first (sending again replaces the link). */
    @Query("select i from AdminInvite i where i.acceptedAt is null and i.cancelledAt is null and i.expiresAt > :now"
            + " and not exists (select 1 from User u where u.email = i.email)"
            + " order by i.updatedAt desc, i.id desc")
    List<AdminInvite> findPending(@Param("now") LocalDateTime now);

    /** Cancels any open invite for an address that has now been registered or claimed by an account. */
    @Modifying(flushAutomatically = true)
    @Query("update AdminInvite i set i.cancelledAt = :at"
            + " where i.email = :email and i.acceptedAt is null and i.cancelledAt is null")
    int cancelOpenForEmail(@Param("email") String email, @Param("at") LocalDateTime at);

    /** Removes unaccepted invites for a verified deleted account's address so its email cannot linger or resurface. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AdminInvite i where i.email = :email and i.acceptedAt is null")
    int deleteUnacceptedByEmail(@Param("email") String email);

    /**
     * Cancels an invite that is still waiting. A bulk update, so the invite's updated_at (when it was last sent) stays
     * as it is: an Undo then lists the invite exactly as before. Deliberately omits {@code clearAutomatically} so the
     * caller's locked acting admin entity stays managed for audit logging.
     */
    @Modifying(flushAutomatically = true)
    @Query("update AdminInvite i set i.cancelledAt = :at"
            + " where i.id = :id and i.acceptedAt is null and i.cancelledAt is null")
    int markCancelled(@Param("id") Long id, @Param("at") LocalDateTime at);

    /**
     * Accepts the invite of a link once, while that link still works (not accepted, cancelled, replaced by a newer
     * sending or expired), even if it changed after it was read.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AdminInvite i set i.acceptedAt = :at where i.tokenHash = :tokenHash"
            + " and i.acceptedAt is null and i.cancelledAt is null and i.expiresAt > :at")
    int markAccepted(@Param("tokenHash") String tokenHash, @Param("at") LocalDateTime at);

    /**
     * Takes a cancel back; a bulk update for the same reason as {@link #markCancelled}, likewise keeping the caller's
     * managed admin entity attached.
     */
    @Modifying(flushAutomatically = true)
    @Query("update AdminInvite i set i.cancelledAt = null"
            + " where i.id = :id and i.acceptedAt is null and i.cancelledAt is not null")
    int clearCancelled(@Param("id") Long id);
}
