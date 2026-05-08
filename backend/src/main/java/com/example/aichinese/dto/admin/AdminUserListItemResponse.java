package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record AdminUserListItemResponse(
        Long userId,
        String fullName,
        String email,
        List<String> roles,
        TeacherStatus teacherStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
