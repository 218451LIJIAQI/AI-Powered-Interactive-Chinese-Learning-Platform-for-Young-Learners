package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

/**
 * The e-mail address of an account: the one a reset, confirmation or invite link belongs to while the link can still be
 * used, or the one an account was just created with (registration, an accepted admin invite).
 */
public record LinkTokenResponse(String email) {

    @Override
    public String toString() {
        return "LinkTokenResponse[email=" + redactValue(email) + "]";
    }
}
