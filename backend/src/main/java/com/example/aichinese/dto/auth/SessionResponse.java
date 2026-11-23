package com.example.aichinese.dto.auth;

import java.time.LocalDateTime;

/**
 * This browser's session (Account › This device): when it was signed in ({@code signedInAt}, the first token of its
 * refresh-token chain); null when the request's token names no session.
 */
public record SessionResponse(LocalDateTime signedInAt) {
}
