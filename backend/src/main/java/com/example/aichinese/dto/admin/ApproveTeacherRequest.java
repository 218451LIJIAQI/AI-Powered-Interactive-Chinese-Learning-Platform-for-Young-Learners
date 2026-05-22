package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.Size;

public record ApproveTeacherRequest(
        @Size(max = 1000, message = "Approval note must be 1000 characters or fewer.")
        String note) {
}
