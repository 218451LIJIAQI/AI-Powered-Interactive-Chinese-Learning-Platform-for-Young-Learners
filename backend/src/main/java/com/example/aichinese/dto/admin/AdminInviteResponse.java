package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

/** A pending admin invite: the invitee has not set a password yet. */
public record AdminInviteResponse(Long id, String email, LocalDateTime sentAt, LocalDateTime expiresAt) {
}
