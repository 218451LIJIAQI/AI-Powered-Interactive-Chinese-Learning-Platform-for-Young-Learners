package com.example.aichinese.dto.auth;

import com.example.aichinese.util.LanguageCodes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Profile settings any signed-in role may change (FRAUTH07). */
public record UpdateProfileRequest(
        @NotBlank(message = "Display name is required.")
        @Size(max = 120, message = "Display name must be 120 characters or fewer.")
        String displayName,

        @NotBlank(message = "Preferred language must be en, ms or zh-CN.")
        @Pattern(regexp = "^(en|ms|zh-CN)$", message = "Preferred language must be en, ms or zh-CN.")
        String preferredLanguage) {

    public UpdateProfileRequest {
        if (displayName != null) {
            displayName = displayName.strip();
        }
        preferredLanguage = LanguageCodes.normalizeRequested(preferredLanguage);
    }
}