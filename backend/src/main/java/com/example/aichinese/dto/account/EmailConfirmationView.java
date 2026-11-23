package com.example.aichinese.dto.account;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

/** The account's address and whether it is confirmed. */
public record EmailConfirmationView(String email, boolean confirmed) {

    @Override
    public String toString() {
        return "EmailConfirmationView[email=" + redactValue(email) + ", confirmed=" + confirmed + "]";
    }
}
