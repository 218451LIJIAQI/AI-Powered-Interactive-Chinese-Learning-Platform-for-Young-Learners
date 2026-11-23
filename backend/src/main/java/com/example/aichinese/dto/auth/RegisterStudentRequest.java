package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.example.aichinese.util.LanguageCodes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterStudentRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @MaxUtf8Bytes
        String password,
        @NotBlank(message = "Confirm password is required")
        @MaxUtf8Bytes
        String confirmPassword,
        @Pattern(regexp = LanguageCodes.SUPPORTED_PATTERN, message = "Preferred language must be one of en, ms, or zh-CN")
        String preferredLanguage) {

    public RegisterStudentRequest {
        fullName = strip(fullName);
        email = strip(email);
        preferredLanguage = stripToNull(preferredLanguage);
    }

    @Override
    public String toString() {
        return "RegisterStudentRequest[fullName=" + redactValue(fullName)
                + ", email=" + redactValue(email)
                + ", password=" + redactValue(password)
                + ", confirmPassword=" + redactValue(confirmPassword)
                + ", preferredLanguage=" + preferredLanguage + "]";
    }
}
