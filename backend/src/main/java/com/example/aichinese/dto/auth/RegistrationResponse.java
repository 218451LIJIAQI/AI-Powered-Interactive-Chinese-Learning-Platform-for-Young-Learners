package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.requireNonBlank;

public record RegistrationResponse(String email) {

    public RegistrationResponse {
        email = requireNonBlank(email, "email");
    }
}
