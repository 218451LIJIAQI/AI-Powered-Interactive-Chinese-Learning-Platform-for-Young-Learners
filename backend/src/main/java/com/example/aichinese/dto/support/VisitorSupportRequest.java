package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.redactLength;
import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripMultiline;

import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.LanguageCodes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Send us a message" on the public Help page: replies go to this e-mail address, written in {@code language}, the
 * page's interface language (optional; a value other than en, ms or zh-CN counts as none, and the replies are English).
 */
public record VisitorSupportRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Choose a topic.")
        SupportTopic topic,

        @NotBlank(message = "Tell us what happened.")
        @Size(max = 4000, message = "The message must be 4000 characters or fewer.")
        String message,

        String language) {

    public VisitorSupportRequest {
        email = strip(email);
        message = AiInputSanitizer.sanitizeUnbounded(stripMultiline(message));
        language = LanguageCodes.normalize(language);
    }

    @Override
    public String toString() {
        return "VisitorSupportRequest[email=" + redactValue(email)
                + ", topic=" + topic
                + ", message=" + redactLength(message)
                + ", language=" + language + "]";
    }
}
