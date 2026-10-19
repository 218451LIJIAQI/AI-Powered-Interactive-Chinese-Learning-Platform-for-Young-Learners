package com.example.aichinese.dto.auth;

import java.util.Locale;

import com.example.aichinese.util.LanguageCodes;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Profile settings any signed-in role may change (D12, FR-AUTH-12). */
@JsonPropertyOrder({ "displayName", "preferredLanguage" })
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
        if (preferredLanguage != null) {
            // en / ms / zh-CN, plus the legacy "zh" mapped to zh-CN; unknown values stay so validation rejects them.
            String stripped = preferredLanguage.strip();
            String normalized = LanguageCodes.normalize(stripped);
            preferredLanguage = normalized != null ? normalized : stripped.toLowerCase(Locale.ROOT);
        }
    }
}