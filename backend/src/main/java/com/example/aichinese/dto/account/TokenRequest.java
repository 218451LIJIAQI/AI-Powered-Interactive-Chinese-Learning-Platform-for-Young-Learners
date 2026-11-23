package com.example.aichinese.dto.account;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A link token from an e-mail (a sign-up or e-mail change confirmation, a password reset or an admin invite). */
public record TokenRequest(
        @NotBlank(message = "Link token is required.")
        @Size(max = 128, message = "Link token must be at most 128 characters.")
        String token) {

    public TokenRequest {
        token = strip(token);
    }

    @Override
    public String toString() {
        return "TokenRequest[token=" + redactValue(token) + "]";
    }
}
