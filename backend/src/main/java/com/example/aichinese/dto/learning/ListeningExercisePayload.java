package com.example.aichinese.dto.learning;

import java.util.List;

public record ListeningExercisePayload(
        String chineseText,
        String pinyin,
        String explanation,
        List<ListeningOptionPayload> options) {
}
