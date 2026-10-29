package com.example.aichinese.dto.support;

/** The number a visitor's message got (support refers to it in the e-mail reply). */
public record SupportRequestCreatedResponse(int number, String email) {
}
