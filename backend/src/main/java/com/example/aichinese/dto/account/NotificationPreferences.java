package com.example.aichinese.dto.account;

/**
 * The e-mail notifications a user can switch off, per role: students get live session reminders and feedback,
 * teachers live session reminders and content reviews, admins the review queue digest. A preference that does not
 * apply to the user's role is null (in a request it is ignored). Account and security e-mails are always sent.
 */
public record NotificationPreferences(
        Boolean liveSessionReminders,
        Boolean feedback,
        Boolean contentReviews,
        Boolean reviewQueues) {
}
