package com.example.aichinese.dto.publicview;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "teacherId",
        "displayName",
        "bio",
        "verified",
        "teachingCategories",
        "courseCount"
})
public record PublicTeacherProfileResponse(
        Long teacherId,
        String displayName,
        String bio,
        boolean verified,
        List<String> teachingCategories,
        int courseCount) {

    public PublicTeacherProfileResponse {
        teachingCategories = teachingCategories == null
                ? List.of()
                : List.copyOf(teachingCategories);
    }
}
