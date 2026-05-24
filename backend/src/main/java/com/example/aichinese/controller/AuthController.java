package com.example.aichinese.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.ForgotPasswordRequest;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST entry points for the authentication surface:
 * registration (student / teacher), login, current-user lookup,
 * logout, forgot-password, and reset-password.
 *
 * <h2>Rate limiting</h2>
 * <p>Public-write endpoints (register, forgot-password) go through
 * {@link PublicAuthRequestLimiter} keyed on the resolved client IP
 * (via {@link ClientIpResolver}) so credential-stuffing and
 * enumeration probes hit the 429 before they reach the persistence
 * layer. Login attempts are limited inside
 * {@link AuthService#login}.</p>
 *
 * <h2>Cache hygiene</h2>
 * <p>{@code GET /me} returns {@code Cache-Control: no-store} so the
 * authenticated JSON profile (email, role list, teacher-status flag)
 * never lands in browser disk or back-forward cache — defence in
 * depth against post-logout replay.</p>
 *
 * <h2>Forgot-password user enumeration</h2>
 * <p>The {@code forgot-password} response is identical whether or
 * not the supplied email exists (rate-limit + uniform message), so an
 * attacker cannot probe for valid accounts via the endpoint.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthService authService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.publicAuthRequestLimiter = publicAuthRequestLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register/student")
    public ApiResponse<RegistrationResponse> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkRegisterAllowed(clientIpResolver.resolve(httpRequest));
        return ApiResponse.success(
                "Student registered successfully.",
                authService.registerStudent(request));
    }

    @PostMapping("/register/teacher")
    public ApiResponse<RegistrationResponse> registerTeacher(
            @Valid @RequestBody RegisterTeacherRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkRegisterAllowed(clientIpResolver.resolve(httpRequest));
        return ApiResponse.success(
                "Teacher registered successfully.",
                authService.registerTeacher(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success("Login successful.", authService.login(request, clientIpResolver.resolve(httpRequest)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        // Cache-Control: no-store keeps the JSON profile out of the browser
        // disk and bfcache so a logged-out user pressing the back button
        // cannot replay an authenticated /me response (which contains the
        // user's email, role list, and teacher-status flag) from a stale
        // cache entry. The complementary frontend behaviour clears the
        // token and reloads on the back-forward navigation, but defense
        // in depth at the response layer is cheap and worthwhile.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Current user retrieved successfully.",
                        authService.getCurrentUser(currentUser)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        authService.logout(currentUser);
        return ApiResponse.success("Logged out successfully.", null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkForgotPasswordAllowed(request.email(), clientIpResolver.resolve(httpRequest));
        authService.forgotPassword(request);
        return ApiResponse.success(
                "If the email is registered, a password reset instruction will be sent once email delivery is configured.",
                null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        // When the token is valid the service updates the password and consumes
        // the reset token; invalid or expired tokens have already surfaced as
        // structured 4xx responses through the exception handler.
        return ApiResponse.success("Password reset request processed successfully.", null);
    }
}
