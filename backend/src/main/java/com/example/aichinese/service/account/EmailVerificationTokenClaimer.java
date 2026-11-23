package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.EmailVerificationToken;
import com.example.aichinese.repository.EmailVerificationTokenRepository;
import com.example.aichinese.service.SecureTokens;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Shared single-use token claim flow for e-mail verification links (sign-up confirmation and e-mail change):
 * look up by SHA-256 hash, write-lock the owning account first, refresh the token row to observe concurrent
 * retirement/deletion, validate availability and expiry, and atomically consume the token.
 */
@Component
@RequiredArgsConstructor
class EmailVerificationTokenClaimer {

    static final String INVALID_LINK = "This confirmation link is invalid or has already been used.";

    private final EntityManager entityManager;

    record Claimed<T extends EmailVerificationToken>(T token, User user, LocalDateTime claimedAt) {
    }

    <T extends EmailVerificationToken> Claimed<T> claim(
            EmailVerificationTokenRepository<T> repository,
            String rawToken,
            BiPredicate<User, T> isStillValid,
            Supplier<ResponseStatusException> onExpired) {
        T token = repository.findByTokenHash(SecureTokens.sha256Hex(rawToken.strip()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, INVALID_LINK));
        User user = token.getUser();
        // All security writes lock the account before claiming or retiring its tokens.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!AccountGuards.isAvailable(user)) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        try {
            entityManager.refresh(token);
        } catch (EntityNotFoundException deleted) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK, deleted);
        }
        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null || !isStillValid.test(user, token)) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw onExpired.get();
        }
        // Consume atomically so a retired or replayed link can never proceed.
        if (repository.consumeActiveToken(token.getId(), now) != 1) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_LINK);
        }
        return new Claimed<>(token, user, now);
    }
}
