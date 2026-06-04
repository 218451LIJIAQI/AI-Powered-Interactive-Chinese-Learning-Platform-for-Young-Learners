package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "email", "password" })
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {

    public LoginRequest {
        if (email != null) {
            email = email.strip();
        }
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
