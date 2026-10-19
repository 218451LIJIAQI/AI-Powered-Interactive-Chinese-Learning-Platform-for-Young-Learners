package com.example.aichinese.dto.dashboard;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "fullName",
        "totalStudents",
        "totalTeachers",
        "pendingTeacherApprovals",
        "aiUsageSummary"
})
public record AdminDashboardResponse(
        String fullName,
        long totalStudents,
        long totalTeachers,
        long pendingTeacherApprovals,
        AiUsageSummary aiUsageSummary) {

    public AdminDashboardResponse {
        Objects.requireNonNull(aiUsageSummary, "aiUsageSummary must not be null");
    }

    @JsonPropertyOrder({ "totalRequests", "successfulRequests" })
    public record AiUsageSummary(
            long totalRequests,
            long successfulRequests) {
    }
}
