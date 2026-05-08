package com.example.aichinese.dto.auth;

import java.util.List;

import com.example.aichinese.entity.enums.TeacherStatus;

public record RegistrationResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        TeacherStatus teacherStatus) {
}
