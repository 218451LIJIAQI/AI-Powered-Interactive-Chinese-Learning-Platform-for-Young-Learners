package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.Size;

public record AdminReportActionRequest(
        @Size(max = 1000, message = "Note must be 1000 characters or fewer.")
        String note) {
}
