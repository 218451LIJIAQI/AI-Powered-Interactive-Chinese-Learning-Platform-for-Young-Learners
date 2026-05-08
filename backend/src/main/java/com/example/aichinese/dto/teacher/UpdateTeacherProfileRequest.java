package com.example.aichinese.dto.teacher;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeacherProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be 120 characters or fewer.")
        String fullName,
        @Size(max = 2000, message = "Bio must be 2000 characters or fewer.")
        String bio,
        @Size(max = 1000, message = "Short introduction must be 1000 characters or fewer.")
        String shortIntroduction,
        List<@Size(max = 60, message = "Each teaching category must be 60 characters or fewer.") String> teachingCategories) {
}
