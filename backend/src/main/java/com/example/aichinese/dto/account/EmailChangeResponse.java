package com.example.aichinese.dto.account;

/** The confirmation link went to {@code pendingEmail}; the e-mail changes when the link is opened before it expires. */
public record EmailChangeResponse(String pendingEmail) {
}
