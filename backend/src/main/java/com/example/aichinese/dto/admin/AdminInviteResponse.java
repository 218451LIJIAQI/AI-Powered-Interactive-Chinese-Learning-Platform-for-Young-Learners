package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;

/** A pending admin invite: the invitee has not set a password yet. */
public record AdminInviteResponse(Long id, String email, LocalDateTime sentAt) {

    @Override
    public String toString() {
        return "AdminInviteResponse[id=" + id + ", email=" + redactValue(email) + ", sentAt=" + sentAt + "]";
    }
}
