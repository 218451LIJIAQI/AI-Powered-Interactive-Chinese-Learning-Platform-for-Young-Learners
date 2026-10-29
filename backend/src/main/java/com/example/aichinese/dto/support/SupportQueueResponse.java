package com.example.aichinese.dto.support;

import java.util.Map;
import java.util.Objects;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.entity.enums.SupportRequestStatus;

/** One tab of the admin support queue and the count of every tab. */
public record SupportQueueResponse(
        Map<SupportRequestStatus, Long> counts,
        PageResponse<SupportRequestSummaryResponse> requests) {

    public SupportQueueResponse {
        counts = Map.copyOf(Objects.requireNonNull(counts, "counts must not be null"));
        Objects.requireNonNull(requests, "requests must not be null");
    }
}
