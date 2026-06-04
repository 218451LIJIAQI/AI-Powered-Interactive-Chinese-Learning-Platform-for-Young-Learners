package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "note" })
public record ApproveTeacherRequest(
        @Size(max = 1000, message = "Approval note must be 1000 characters or fewer.")
        String note) {

    public ApproveTeacherRequest {
        note = strip(note);
    }
}
