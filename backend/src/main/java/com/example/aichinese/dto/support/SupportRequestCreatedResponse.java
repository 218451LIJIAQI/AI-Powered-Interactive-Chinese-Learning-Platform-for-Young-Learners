package com.example.aichinese.dto.support;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

/** The number a new request got (support's e-mail reply refers to it) and the requester's address. */
public record SupportRequestCreatedResponse(int number, String email) {

    @Override
    public String toString() {
        return "SupportRequestCreatedResponse[number=" + number + ", email=" + redactValue(email) + "]";
    }
}
