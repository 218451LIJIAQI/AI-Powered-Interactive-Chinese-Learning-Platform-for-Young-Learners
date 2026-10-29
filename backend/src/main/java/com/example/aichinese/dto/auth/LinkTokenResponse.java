package com.example.aichinese.dto.auth;

/** A reset, confirmation or invite link that can still be used: the address it belongs to. */
public record LinkTokenResponse(String email) {
}
