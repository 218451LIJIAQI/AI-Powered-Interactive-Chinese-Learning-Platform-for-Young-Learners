package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.util.LanguageCodes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The invited admin opens the invite link, gives their name and sets a password; the admin account is created with
 * the interface language of the page (optional, as at sign-up).
 */
public record AcceptAdminInviteRequest(
        @NotBlank(message = "Invite token is required.")
        @Size(max = 128, message = "Invite token must be at most 128 characters.")
        String token,
        @NotBlank(message = "Full name is required")
        @Pattern(regexp = DtoNormalization.NO_CONTROL_CHARS_PATTERN, message = "Full name contains unsupported control characters")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes
        String password,
        @NotBlank(message = "Confirm password is required")
        @MaxUtf8Bytes
        String confirmPassword,
        @Pattern(regexp = LanguageCodes.SUPPORTED_PATTERN, message = "Preferred language must be one of en, ms, or zh-CN")
        String preferredLanguage) {

    public AcceptAdminInviteRequest {
        token = strip(token);
        fullName = strip(fullName);
        preferredLanguage = stripToNull(preferredLanguage);
    }

    @Override
    public String toString() {
        return "AcceptAdminInviteRequest[token=" + redactValue(token) + ", fullName=" + redactValue(fullName)
                + ", password=" + redactValue(password) + ", confirmPassword=" + redactValue(confirmPassword)
                + ", preferredLanguage=" + preferredLanguage + "]";
    }
}
