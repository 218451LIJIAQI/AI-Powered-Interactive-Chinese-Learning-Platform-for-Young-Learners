package com.example.aichinese.dto.account;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

/** Everything the Account page shows besides the profile: a pending e-mail change and the e-mail preferences. */
public record AccountSettingsResponse(
        String fullName,
        String email,
        /** The address a confirmation link was sent to, while that link can still be opened. */
        String pendingEmail,
        NotificationPreferences notifications) {

    @Override
    public String toString() {
        return "AccountSettingsResponse[fullName=" + redactValue(fullName)
                + ", email=" + redactValue(email)
                + ", pendingEmail=" + redactValue(pendingEmail)
                + ", notifications=" + notifications + "]";
    }
}
