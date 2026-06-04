package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "liveClassId", "title", "meetingPlatform", "meetingLink" })
public record JoinLiveClassResponse(
        Long liveClassId,
        String title,
        String meetingPlatform,
        String meetingLink) {

    public JoinLiveClassResponse {
        Objects.requireNonNull(liveClassId, "liveClassId must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
