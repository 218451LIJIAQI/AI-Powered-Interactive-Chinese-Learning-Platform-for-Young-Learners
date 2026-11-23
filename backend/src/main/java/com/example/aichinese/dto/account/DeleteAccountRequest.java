package com.example.aichinese.dto.account;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The user types their e-mail address to confirm that the account should be deleted. */
public record DeleteAccountRequest(
        @NotBlank(message = "Type your email address to confirm.")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String confirmEmail) {

    public DeleteAccountRequest {
        confirmEmail = strip(confirmEmail);
    }

    @Override
    public String toString() {
        return "DeleteAccountRequest[confirmEmail=" + redactValue(confirmEmail) + "]";
    }
}
