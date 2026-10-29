package com.example.aichinese.dto.account;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.dto.PublishedClassSummary;

/** Everything the Account page shows besides the profile: a pending e-mail change, preferences, account deletion. */
public record AccountSettingsResponse(
        String fullName,
        String email,
        /** The address a confirmation link was sent to, while that link can still be opened. */
        String pendingEmail,
        NotificationPreferences notifications,
        Deletion deletion) {

    public AccountSettingsResponse {
        Objects.requireNonNull(notifications, "notifications must not be null");
        Objects.requireNonNull(deletion, "deletion must not be null");
    }

    /** Why the account cannot be deleted from the Account page. */
    public enum DeletionBlock {
        /** Admin accounts are deactivated by another admin instead. */
        ADMIN_ACCOUNT,
        /** A teacher archives their published classes first. */
        PUBLISHED_CLASSES
    }

    /** {@code blockedBy} is null when the account can be deleted. */
    public record Deletion(DeletionBlock blockedBy, List<PublishedClassSummary> publishedClasses) {

        public Deletion {
            publishedClasses = DtoNormalization.immutableListOrEmpty(publishedClasses);
        }
    }
}
