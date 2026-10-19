package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/** Draggable item; its target zone is NOT included (no answer key before an attempt). */
public record StudentDragDropItemResponse(
        Long id,
        String dragText,
        String pinyin,
        String imageUrl,
        Integer sortOrder) {

    public StudentDragDropItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(dragText, "dragText must not be null");
    }
}