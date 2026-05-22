package com.example.aichinese.dto.content;

public record PublicTeacherSummaryResponse(
        Long teacherId,
        String displayName,
        boolean verified) {
}
