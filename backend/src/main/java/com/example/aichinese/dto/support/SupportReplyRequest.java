package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportReplyRequest(
        @NotBlank(message = "Write a reply first.")
        @Size(max = 4000, message = "The message must be 4000 characters or fewer.")
        String body) {

    public SupportReplyRequest {
        body = strip(body);
    }
}
