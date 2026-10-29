package com.example.aichinese.dto.learn;

import java.util.List;

/** A unit's guidebook: key phrases (heard with Listen) and tips. */
public record GuidebookResponse(
        Long unitId,
        int number,
        String title,
        String titleZh,
        List<Phrase> phrases,
        List<String> tips) {

    public GuidebookResponse {
        phrases = phrases == null ? List.of() : List.copyOf(phrases);
        tips = tips == null ? List.of() : List.copyOf(tips);
    }

    public record Phrase(String hanzi, String pinyin, String meaning) {
    }
}
