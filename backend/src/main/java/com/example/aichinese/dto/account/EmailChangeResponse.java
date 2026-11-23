package com.example.aichinese.dto.account;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

/** The confirmation link went to {@code pendingEmail}; the e-mail changes when the link is opened before it expires. */
public record EmailChangeResponse(String pendingEmail) {

    @Override
    public String toString() {
        return "EmailChangeResponse[pendingEmail=" + redactValue(pendingEmail) + "]";
    }
}
