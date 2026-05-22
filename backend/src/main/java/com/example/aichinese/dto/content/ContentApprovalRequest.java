package com.example.aichinese.dto.content;

import jakarta.validation.constraints.Size;

public record ContentApprovalRequest(
        @Size(max = 1000, message = "Approval note must not exceed 1000 characters.")
        String note) {
}
