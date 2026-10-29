package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.example.aichinese.entity.enums.SupportTopic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The New request form of a signed-in student or teacher (sent as multipart, with an optional screenshot). */
public record CreateSupportRequest(
        @NotNull(message = "Choose a topic.")
        SupportTopic topic,

        @NotBlank(message = "Add a subject.")
        @Size(max = 150, message = "The subject must be 150 characters or fewer.")
        String subject,

        @NotBlank(message = "Tell us what happened.")
        @Size(max = 4000, message = "The message must be 4000 characters or fewer.")
        String message) {

    public CreateSupportRequest {
        subject = strip(subject);
        message = strip(message);
    }
}
