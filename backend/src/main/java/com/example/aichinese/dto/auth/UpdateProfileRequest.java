package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.util.LanguageCodes;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profile settings any signed-in role may change (FRAUTH07). Omitted fields keep their stored values.
 */
public record UpdateProfileRequest(
        @Pattern(regexp = DtoNormalization.NON_BLANK_PATTERN, message = "Display name is required.")
        @Pattern(regexp = DtoNormalization.NO_CONTROL_CHARS_PATTERN, message = "Display name contains unsupported control characters.")
        @Size(max = 120, message = "Display name must be 120 characters or fewer.")
        String displayName,

        @Pattern(regexp = LanguageCodes.SUPPORTED_PATTERN, message = "Preferred language must be en, ms or zh-CN.")
        String preferredLanguage) {

    public UpdateProfileRequest {
        displayName = strip(displayName);
        preferredLanguage = strip(preferredLanguage);
    }

    @JsonIgnore
    @AssertTrue(message = "Provide a display name or preferred language.")
    public boolean isUpdateRequested() {
        return displayName != null || preferredLanguage != null;
    }

    @Override
    public String toString() {
        return "UpdateProfileRequest[displayName=" + redactValue(displayName)
                + ", preferredLanguage=" + preferredLanguage + "]";
    }
}
