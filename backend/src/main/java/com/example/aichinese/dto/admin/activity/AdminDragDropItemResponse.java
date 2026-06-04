package com.example.aichinese.dto.admin.activity;

public record AdminDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {
}
