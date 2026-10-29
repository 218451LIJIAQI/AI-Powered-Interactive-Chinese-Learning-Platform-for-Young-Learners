package com.example.aichinese.dto.learn;

import java.util.List;

import com.example.aichinese.entity.enums.PracticeType;

/**
 * A finished run: right answers per item, whether every item was right (the set's XP is paid the first time that
 * happens), the XP earned (a quiz's in-a-row bonus included) and the next set of the same type.
 */
public record PracticeCompleteResponse(
        PracticeType type,
        Long setId,
        String title,
        int correctCount,
        int itemCount,
        boolean allCorrect,
        List<ItemResult> results,
        NextSet nextSet,
        XpResult xp) {

    public PracticeCompleteResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public record ItemResult(String key, boolean correct) {
    }

    public record NextSet(Long id, String title) {
    }
}
