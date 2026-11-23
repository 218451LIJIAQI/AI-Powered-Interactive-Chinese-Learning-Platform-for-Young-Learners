package com.example.aichinese.dto.learn;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PracticeType;

/**
 * A Practice set in the admin details drawer and edit form: its items with answers, how many students played it and
 * its history (newest first). A tracing set has its strokes, all together and per character
 * ({@code characterStrokes}, null when not given).
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
        long playedBy,
        List<PracticeItemData> items,
        Integer strokeCount,
        List<Integer> characterStrokes,
        List<HistoryEntry> history) {

    @Override
    public String toString() {
        return "AdminPracticeSetDetail[id=" + id + ", type=" + type + ", title=" + title
                + ", stage=" + stage + ", hskLevel=" + hskLevel + ", xpReward=" + xpReward
                + ", status=" + status + ", createdBy=" + redactValue(createdBy) + ", playedBy=" + playedBy
                + ", items=" + (items == null ? 0 : items.size())
                + ", strokeCount=" + strokeCount
                + ", history=" + (history == null ? 0 : history.size()) + "]";
    }

    /** {@code action}: CREATED, UPDATED, PUBLISHED, UNPUBLISHED, ARCHIVED or RESTORED; actor null for the platform. */
    public record HistoryEntry(String action, String actor, LocalDateTime at) {

        @Override
        public String toString() {
            return "HistoryEntry[action=" + action + ", actor=" + redactValue(actor) + ", at=" + at + "]";
        }
    }
}
