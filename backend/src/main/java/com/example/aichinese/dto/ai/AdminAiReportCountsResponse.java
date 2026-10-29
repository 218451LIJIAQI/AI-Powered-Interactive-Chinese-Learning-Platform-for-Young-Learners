package com.example.aichinese.dto.ai;

/** How many AI reports are open, resolved and dismissed (the "Reports (N open)" tab). */
public record AdminAiReportCountsResponse(long open, long resolved, long dismissed) {
}
