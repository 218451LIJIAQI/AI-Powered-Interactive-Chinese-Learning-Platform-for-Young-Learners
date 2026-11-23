package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 128, message = "Reset token must be at most 128 characters")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes
        String password,
        @NotBlank(message = "Confirm password is required")
        @MaxUtf8Bytes
        String confirmPassword) {

    public ResetPasswordRequest {
        token = strip(token);
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=" + redactValue(token)
                + ", password=" + redactValue(password)
                + ", confirmPassword=" + redactValue(confirmPassword) + "]";
    }
}
