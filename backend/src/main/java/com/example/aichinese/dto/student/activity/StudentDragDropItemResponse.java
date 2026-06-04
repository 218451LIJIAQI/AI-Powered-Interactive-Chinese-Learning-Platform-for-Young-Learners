package com.example.aichinese.dto.student.activity;

import java.util.Objects;

public record StudentDragDropItemResponse(
        Long id,
        String dragText,
        String targetZone,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {

    public StudentDragDropItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(dragText, "dragText must not be null");
        Objects.requireNonNull(targetZone, "targetZone must not be null");
    }
}
