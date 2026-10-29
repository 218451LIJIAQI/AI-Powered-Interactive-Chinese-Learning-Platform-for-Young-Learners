package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password,
        /** "Keep me signed in": a persistent refresh cookie; otherwise it ends with the browser session (default true). */
        Boolean keepSignedIn) {

    public LoginRequest {
        if (email != null) {
            email = email.strip();
        }
        keepSignedIn = keepSignedIn == null || keepSignedIn;
    }

    @JsonIgnore
    @AssertTrue(message = "Password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(password);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***REDACTED***]";
    }
}
