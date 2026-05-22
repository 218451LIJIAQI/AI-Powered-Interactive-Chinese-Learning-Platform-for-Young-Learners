package com.example.aichinese.dto.publicview;

import java.util.List;

public record PublicTeacherProfileResponse(
        Long teacherId,
        String displayName,
        String bio,
        boolean verified,
        List<String> teachingCategories,
        int courseCount) {
}
