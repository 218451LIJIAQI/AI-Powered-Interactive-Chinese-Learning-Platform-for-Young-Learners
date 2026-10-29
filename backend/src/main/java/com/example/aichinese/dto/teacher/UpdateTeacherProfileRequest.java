package com.example.aichinese.dto.teacher;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The public profile students see: the teacher's name and bio. */
public record UpdateTeacherProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be 120 characters or fewer.")
        String fullName,

        // FRAUTH02: the same rule as the teacher sign-up form, so an approved teacher cannot clear the required bio.
        @NotBlank(message = "Teacher bio is required")
        @Size(min = 20, max = 1000, message = "Teacher bio must be between 20 and 1000 characters")
        String bio) {

    public UpdateTeacherProfileRequest {
        fullName = strip(fullName);
        bio = stripToNull(bio);
    }
}
