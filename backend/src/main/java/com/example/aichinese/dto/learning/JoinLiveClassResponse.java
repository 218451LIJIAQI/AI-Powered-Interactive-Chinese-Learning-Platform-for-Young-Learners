package com.example.aichinese.dto.learning;

public record JoinLiveClassResponse(
        Long liveClassId,
        String title,
        String meetingPlatform,
        String meetingLink) {
}
