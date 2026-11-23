package com.example.aichinese.dto.account;

/**
 * The e-mail notifications a user can switch off: an admin's "Daily summary" ({@code reviewQueues}: AI reports and
 * support requests waiting). Learners have none to switch (account and security e-mails are always sent), so it is
 * null for them, and ignored in a learner's request.
 */
public record NotificationPreferences(Boolean reviewQueues) {
}
