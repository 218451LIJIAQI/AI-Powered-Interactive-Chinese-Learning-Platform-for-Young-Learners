package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.TeacherStatus;

public record TeacherOwnerSummaryResponse(
        Long teacherId,
        Long userId,
        String fullName,
        TeacherStatus teacherStatus) {
}
