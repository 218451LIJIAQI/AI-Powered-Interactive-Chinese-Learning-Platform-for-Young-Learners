package com.example.aichinese.dto.learning;

import java.util.List;

public record MatchingGamePlayResponse(
        Long id,
        Long lessonId,
        String title,
        List<MatchingGameItemResponse> items) {
}
