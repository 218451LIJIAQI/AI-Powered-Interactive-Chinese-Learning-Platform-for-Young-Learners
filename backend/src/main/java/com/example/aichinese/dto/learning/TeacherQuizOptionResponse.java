package com.example.aichinese.dto.learning;

public record TeacherQuizOptionResponse(
        Long id,
        String optionText,
        boolean correctOption,
        Integer displayOrder) {
}
