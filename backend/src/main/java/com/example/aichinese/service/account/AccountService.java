package com.example.aichinese.service.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.account.AccountSettingsResponse;
import com.example.aichinese.dto.account.NotificationPreferences;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.security.SecurityUserDetails;

import lombok.RequiredArgsConstructor;

/** The Account page: the pending e-mail change and the e-mail notification preferences. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final CurrentAccount currentAccount;
    private final AccountEmailService accountEmailService;

    @Transactional(readOnly = true)
    public AccountSettingsResponse settings(SecurityUserDetails currentUser) {
        User user = currentAccount.findWithRolesById(currentUser.getUserId());
        RoleName role = user.requirePrimaryRole();
        return new AccountSettingsResponse(
                user.getDisplayName(),
                user.getEmail(),
                accountEmailService.pendingEmail(user.getId()).orElse(null),
                preferencesOf(user, role));
    }

    /** Applies the preferences that belong to the user's role; the others are ignored. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationPreferences updatePreferences(SecurityUserDetails currentUser, NotificationPreferences request) {
        User user = currentAccount.lock(currentUser);
        RoleName role = user.requirePrimaryRole();
        if (role == RoleName.ADMIN && request.reviewQueues() != null) {
            user.setNotifyReviewQueues(request.reviewQueues());
        }
        return preferencesOf(user, role);
    }

    private static NotificationPreferences preferencesOf(User user, RoleName role) {
        return new NotificationPreferences(role == RoleName.ADMIN ? user.isNotifyReviewQueues() : null);
    }
}
