package com.example.aichinese.dto.learn;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/** A row of the admin Practice library. */
public record AdminPracticeSetRow(
        Long id,
        PracticeType type,
        String title,
        LearningStage stage,
        Integer hskLevel,
        int itemCount,
        ContentStatus status,
        LocalDateTime updatedAt) {
}
