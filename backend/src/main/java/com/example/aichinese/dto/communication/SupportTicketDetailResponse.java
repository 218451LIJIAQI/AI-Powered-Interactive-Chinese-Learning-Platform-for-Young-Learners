package com.example.aichinese.dto.communication;

import java.util.List;

public record SupportTicketDetailResponse(
        SupportTicketResponse ticket,
        List<SupportTicketMessageResponse> messages) {
}
