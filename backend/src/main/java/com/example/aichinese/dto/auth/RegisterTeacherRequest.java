package com.example.aichinese.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "fullName", "email", "password", "confirmPassword", "bio" })
public record RegisterTeacherRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,
        @NotBlank(message = "Confirm password is required")
        @Size(max = 72, message = "Confirm password must be at most 72 characters")
        String confirmPassword,
        @Size(max = 2000, message = "Bio must be at most 2000 characters")
        String bio) {

    public RegisterTeacherRequest {
        if (fullName != null) {
            fullName = fullName.strip();
        }
        if (email != null) {
            email = email.strip();
        }
        if (bio != null) {
            bio = bio.strip();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "Password and confirm password must be at most 72 bytes when encoded as UTF-8")
    public boolean isPasswordsWithinBcryptByteLimit() {
        return AuthDtoValidation.isWithinBcryptByteLimit(password)
                && AuthDtoValidation.isWithinBcryptByteLimit(confirmPassword);
    }

    @Override
    public String toString() {
        return "RegisterTeacherRequest[fullName=" + fullName
                + ", email=" + email
                + ", password=***REDACTED***"
                + ", confirmPassword=***REDACTED***"
                + ", bio=" + bio + "]";
    }
}
