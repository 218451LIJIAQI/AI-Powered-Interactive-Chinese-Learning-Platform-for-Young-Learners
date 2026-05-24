package com.example.aichinese.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials submitted to {@code POST /api/v1/auth/login}.
 *
 * <p>Validation only enforces shape (non-blank, well-formed email,
 * length caps); the actual authentication outcome is decided by
 * {@code AuthenticationManager} in {@code AuthService} and reported
 * back as a {@code 401 Unauthorized} when the credentials do not match,
 * or as a structured {@code 429 Too Many Requests} when
 * {@code LoginAttemptLimiter} has locked the account out.</p>
 *
 * <p><b>Why the 72-character password cap.</b> BCrypt — the password
 * hash function used by {@code passwordEncoder} — silently truncates
 * inputs to 72 bytes. Capping the request at the same length ensures
 * the {@code @NotBlank} validator and the hash function agree, and
 * prevents users from believing their long passphrase is more secure
 * than the actually-hashed prefix.</p>
 *
 * <p><b>Why the 320-character email cap.</b> RFC 5321 §4.5.3.1 limits
 * the {@code local@domain} pair to 320 octets total. Anything longer
 * is non-deliverable in practice.</p>
 *
 * <p>The email is later normalised by {@code AuthService.normalizeEmail}
 * (trim + lowercase) before any database lookup so case variation in
 * the input does not produce duplicate accounts.</p>
 *
 * @param email    user-facing login identifier; case-insensitive at
 *                 the service layer
 * @param password raw password; never stored — hashed with BCrypt by
 *                 the authentication manager
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {
}
