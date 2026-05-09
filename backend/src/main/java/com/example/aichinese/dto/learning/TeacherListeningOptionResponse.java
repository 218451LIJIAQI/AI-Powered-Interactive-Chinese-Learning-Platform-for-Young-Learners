package com.example.aichinese.dto.learning;

public record TeacherListeningOptionResponse(
        String optionText,
        boolean correctOption,
        Integer displayOrder) {
}
