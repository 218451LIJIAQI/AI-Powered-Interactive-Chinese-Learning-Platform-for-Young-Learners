package com.example.aichinese.dto.ai;

import java.util.List;

public record AiProgressInsightResponse(
        String performanceSummary,
        List<String> weakAreas,
        List<String> nextStepSuggestions,
        String studentEncouragement,
        String teacherSuggestion,
        String status) {
}
