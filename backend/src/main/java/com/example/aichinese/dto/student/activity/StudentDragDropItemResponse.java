package com.example.aichinese.dto.student.activity;

public record StudentDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {
}
