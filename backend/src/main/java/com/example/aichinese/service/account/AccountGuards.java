package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.UserRepository;

/**
 * The account checks writes share: whether an address is free for an account (the reserved address of a deleted
 * account never is) and whether an account is still available or a request's session still holds (checked on the rows a
 * write loaded and locked).
 */
public final class AccountGuards {

    public static final String ACCOUNT_UNAVAILABLE_MESSAGE =
            "This account is currently unavailable. Please contact the platform administrator.";

    /** The domain of the address a deleted account is given; ".invalid" is reserved (RFC 2606) and receives no mail. */
    public static final String DELETED_ACCOUNT_DOMAIN = "@deleted.invalid";

    private AccountGuards() {
    }

    /** The address a deleted account keeps: unique through its id, so the e-mail column stays unique. */
    public static String deletedAccountEmail(Long userId) {
        return "deleted-" + userId + DELETED_ACCOUNT_DOMAIN;
    }

    /**
     * 400 when the lower-case {@code email} cannot become an account's address (sign-up, admin invite, e-mail change):
     * an account holds it, or it has the form deleted accounts are given, as an account holding it would block a later
     * deletion.
     */
    public static void requireUnregisteredEmail(UserRepository userRepository, String email) {
        if (email.endsWith(DELETED_ACCOUNT_DOMAIN) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already registered.");
        }
    }

    /** Active and not deleted: the account can sign in and act. */
    public static boolean isAvailable(User user) {
        return user.isActive() && user.getDeletedAt() == null;
    }

    /** 403 when the account was deactivated or deleted (sign-in, and the account's own security changes). */
    public static void requireAvailable(User user) {
        if (!isAvailable(user)) {
            throw new ResponseStatusException(FORBIDDEN, ACCOUNT_UNAVAILABLE_MESSAGE);
        }
    }

    /** 401 when the account was deactivated, deleted or signed out since the request's token was issued. */
    public static void requireCurrent(User user, long sessionVersion) {
        if (!isAvailable(user) || user.getSessionVersion() != sessionVersion) {
            throw new ResponseStatusException(UNAUTHORIZED, FriendlyErrorMessageResolver.sessionExpiredMessage());
        }
    }
}
