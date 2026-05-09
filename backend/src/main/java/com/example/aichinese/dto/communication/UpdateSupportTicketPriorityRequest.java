package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;

public record UpdateSupportTicketPriorityRequest(
        @NotBlank(message = "Priority is required.")
        String priority) {
}
