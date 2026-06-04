package com.example.aichinese.dto.communication;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "updatedCount" })
public record BulkOperationResponse(
        long updatedCount) {

    public BulkOperationResponse {
        if (updatedCount < 0L) {
            throw new IllegalArgumentException(
                    "updatedCount must be non-negative but was " + updatedCount);
        }
    }
}
