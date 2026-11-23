package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,
        @NotBlank(message = "Password is required")
        @MaxUtf8Bytes
        String password,
        /** "Keep me signed in": a persistent refresh cookie; otherwise it ends with the browser session (default true). */
        Boolean keepSignedIn) {

    public LoginRequest {
        email = strip(email);
        keepSignedIn = keepSignedIn == null || keepSignedIn;
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + redactValue(email) + ", password=" + redactValue(password)
                + ", keepSignedIn=" + keepSignedIn + "]";
    }
}
