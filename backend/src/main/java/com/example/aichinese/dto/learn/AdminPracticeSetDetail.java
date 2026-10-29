package com.example.aichinese.dto.learn;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * A Practice set in the admin details drawer and edit form: its items with answers, how many students played it and
 * its history (newest first).
 */
public record AdminPracticeSetDetail(
        Long id,
        PracticeType type,
        String title,
        LearningStage stage,
        Integer hskLevel,
        int xpReward,
        ContentStatus status,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long playedBy,
        List<PracticeItemData> items,
        List<StoryLine> storyLines,
        Integer strokeCount,
        List<HistoryEntry> history) {

    public AdminPracticeSetDetail {
        items = items == null ? List.of() : List.copyOf(items);
        storyLines = storyLines == null ? List.of() : List.copyOf(storyLines);
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** {@code action}: CREATED, UPDATED, PUBLISHED, UNPUBLISHED, ARCHIVED or RESTORED; actor null for the platform. */
    public record HistoryEntry(String action, String actor, LocalDateTime at) {
    }
}
