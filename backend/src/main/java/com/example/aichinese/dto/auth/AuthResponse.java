package com.example.aichinese.dto.auth;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record AuthResponse(
        String token,
        UserSummary user) {

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            List<String> roles,
            TeacherStatus teacherStatus) {
    }
}
