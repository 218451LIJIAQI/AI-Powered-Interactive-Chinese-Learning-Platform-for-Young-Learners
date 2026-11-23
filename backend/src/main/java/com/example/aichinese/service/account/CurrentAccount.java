package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

/**
 * Loads the account owned by a request and checks both its session version and (when present) its browser session
 * family after acquiring a write lock.
 */
@Component
@RequiredArgsConstructor
public class CurrentAccount {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager entityManager;

    /**
     * The row of the signed-in user by the request principal's id; user rows are anonymised rather than deleted, so
     * absence is treated as an invalidated session (401). Lookups by any other user id need their own 404 check.
     */
    public User findById(Long userId) {
        return userRepository.findById(userId).orElseThrow(CurrentAccount::authenticatedUserMissing);
    }

    /** {@link #findById(Long)} with the account's roles eagerly loaded. */
    public User findWithRolesById(Long userId) {
        return userRepository.findWithRolesById(userId).orElseThrow(CurrentAccount::authenticatedUserMissing);
    }

    /** The signed-in user; refused when the account or the browser session has ended since the token was issued. */
    public User require(SecurityUserDetails currentUser) {
        User user = findById(currentUser.getUserId());
        AccountGuards.requireCurrent(user, currentUser.getSessionVersion());
        if (currentUser.getSessionId() != null
                && !refreshTokenRepository.existsByUserIdAndFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
                        currentUser.getUserId(), currentUser.getSessionId(), LocalDateTime.now())) {
            throw sessionExpired();
        }
        return user;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User lock(SecurityUserDetails principal) {
        return lock(principal.getUserId(), principal.getSessionVersion(), principal.getSessionId());
    }

    /**
     * Locks the original account and validates both {@code sessionVersion} and, when present, that the browser's
     * refresh-token family ({@code sessionId}) has not been signed out or expired.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User lock(Long userId, long sessionVersion, String sessionId) {
        User user = findWithRolesById(userId);
        // Refresh also replaces an earlier managed snapshot after waiting for account deletion/revocation.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        AccountGuards.requireCurrent(user, sessionVersion);
        requireActiveSession(userId, sessionId);
        return user;
    }

    /**
     * Locks both the acting account and {@code other} in deterministic ascending id order (preventing deadlocks when
     * two accounts act on each other), then verifies the actor's session version and browser session family.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User lockWith(SecurityUserDetails actor, User other) {
        User user = findById(actor.getUserId());
        AccountGuards.lockInIdOrder(entityManager, user, other);
        AccountGuards.requireCurrent(user, actor.getSessionVersion());
        requireActiveSession(user.getId(), actor.getSessionId());
        return user;
    }

    /**
     * Verifies under a {@code PESSIMISTIC_READ} lock on {@code refresh_tokens} that the caller's browser session family
     * has not been signed out or expired. Callers must already hold the lock on {@code users} so global lock order
     * ({@code users -> refresh_tokens}) is preserved.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveSession(Long userId, String sessionId) {
        if (sessionId != null
                && refreshTokenRepository.findActiveSessionTokenIdsLocked(userId, sessionId, LocalDateTime.now())
                        .isEmpty()) {
            throw sessionExpired();
        }
    }

    private static ResponseStatusException authenticatedUserMissing() {
        return new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found.");
    }

    private static ResponseStatusException sessionExpired() {
        return new ResponseStatusException(UNAUTHORIZED, FriendlyErrorMessageResolver.sessionExpiredMessage());
    }
}
