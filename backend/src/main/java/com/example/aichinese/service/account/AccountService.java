package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PublishedClassSummary;
import com.example.aichinese.dto.account.AccountSettingsResponse;
import com.example.aichinese.dto.account.AccountSettingsResponse.Deletion;
import com.example.aichinese.dto.account.AccountSettingsResponse.DeletionBlock;
import com.example.aichinese.dto.account.NotificationPreferences;
import com.example.aichinese.entity.EmailChangeToken;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

/** The Account page: the pending e-mail change, e-mail notification preferences and whether the account can be deleted. */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final PublishedClassLookup publishedClassLookup;

    public AccountService(
            UserRepository userRepository,
            EmailChangeTokenRepository emailChangeTokenRepository,
            PublishedClassLookup publishedClassLookup) {
        this.userRepository = userRepository;
        this.emailChangeTokenRepository = emailChangeTokenRepository;
        this.publishedClassLookup = publishedClassLookup;
    }

    @Transactional(readOnly = true)
    public AccountSettingsResponse settings(SecurityUserDetails currentUser) {
        User user = requireUser(currentUser);
        RoleName role = AccountRoles.primary(user);
        String pendingEmail = emailChangeTokenRepository
                .findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user.getId(), LocalDateTime.now())
                .map(EmailChangeToken::getNewEmail)
                .orElse(null);
        return new AccountSettingsResponse(
                user.getDisplayName(),
                user.getEmail(),
                pendingEmail,
                preferencesOf(user, role),
                deletionOf(user, role));
    }

    /** Applies the preferences that belong to the user's role; the others are ignored. */
    @Transactional
    public NotificationPreferences updatePreferences(SecurityUserDetails currentUser, NotificationPreferences request) {
        User user = requireUser(currentUser);
        RoleName role = AccountRoles.primary(user);
        if (role != RoleName.ADMIN && request.liveSessionReminders() != null) {
            user.setNotifyLiveSessionReminders(request.liveSessionReminders());
        }
        if (role == RoleName.STUDENT && request.feedback() != null) {
            user.setNotifyFeedback(request.feedback());
        }
        if (role == RoleName.TEACHER && request.contentReviews() != null) {
            user.setNotifyContentReviews(request.contentReviews());
        }
        if (role == RoleName.ADMIN && request.reviewQueues() != null) {
            user.setNotifyReviewQueues(request.reviewQueues());
        }
        userRepository.save(user);
        return preferencesOf(user, role);
    }

    private static NotificationPreferences preferencesOf(User user, RoleName role) {
        return switch (role) {
            case STUDENT -> new NotificationPreferences(
                    user.isNotifyLiveSessionReminders(), user.isNotifyFeedback(), null, null);
            case TEACHER -> new NotificationPreferences(
                    user.isNotifyLiveSessionReminders(), null, user.isNotifyContentReviews(), null);
            case ADMIN -> new NotificationPreferences(null, null, null, user.isNotifyReviewQueues());
        };
    }

    private Deletion deletionOf(User user, RoleName role) {
        if (role == RoleName.ADMIN) {
            return new Deletion(DeletionBlock.ADMIN_ACCOUNT, List.of());
        }
        List<PublishedClassSummary> published = role == RoleName.TEACHER
                ? publishedClassLookup.publishedClasses(user)
                : List.of();
        return published.isEmpty()
                ? new Deletion(null, List.of())
                : new Deletion(DeletionBlock.PUBLISHED_CLASSES, published);
    }

    private User requireUser(SecurityUserDetails currentUser) {
        return userRepository.findWithRolesById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));
    }
}
