package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.example.aichinese.entity.enums.SupportTopic;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "Send us a message" on the public Help page: replies go to this e-mail address. */
public record VisitorSupportRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Choose a topic.")
        SupportTopic topic,

        @NotBlank(message = "Tell us what happened.")
        @Size(max = 4000, message = "The message must be 4000 characters or fewer.")
        String message) {

    public VisitorSupportRequest {
        email = strip(email);
        message = strip(message);
    }
}
