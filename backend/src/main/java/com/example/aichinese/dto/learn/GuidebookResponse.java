package com.example.aichinese.dto.learn;

import java.util.List;

/** A unit's guidebook: key phrases (heard with Listen) and tips. */
public record GuidebookResponse(List<Phrase> phrases, List<String> tips) {

    public record Phrase(String hanzi, String pinyin, String meaning) {
    }
}
