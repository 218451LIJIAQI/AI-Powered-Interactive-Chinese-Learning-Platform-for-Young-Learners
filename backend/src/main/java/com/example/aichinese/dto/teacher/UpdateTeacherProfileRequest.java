package com.example.aichinese.dto.teacher;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.strippedStringListOrNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "fullName", "bio", "shortIntroduction", "teachingCategories" })
public record UpdateTeacherProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be 120 characters or fewer.")
        String fullName,

        @Size(max = 2000, message = "Bio must be 2000 characters or fewer.")
        String bio,

        @Size(max = 1000, message = "Short introduction must be 1000 characters or fewer.")
        String shortIntroduction,

        @Size(max = 12, message = "Teaching categories must not exceed 12 entries.")
        List<@Size(max = 60, message = "Each teaching category must be 60 characters or fewer.") String> teachingCategories) {

    public UpdateTeacherProfileRequest {
        fullName = strip(fullName);
        bio = strip(bio);
        shortIntroduction = strip(shortIntroduction);
        teachingCategories = strippedStringListOrNull(teachingCategories);
    }
}
