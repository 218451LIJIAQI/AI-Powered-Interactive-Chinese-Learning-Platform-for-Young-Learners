package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({ "status" })
public record UpdateLiveClassStatusRequest(
        @NotBlank(message = "Status is required.")
        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status) {

    public UpdateLiveClassStatusRequest {
        status = strip(status);
    }
}
