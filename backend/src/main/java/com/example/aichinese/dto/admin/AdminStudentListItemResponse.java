package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

public record AdminStudentListItemResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String preferredLanguage,
        LocalDateTime createdAt) {
}
