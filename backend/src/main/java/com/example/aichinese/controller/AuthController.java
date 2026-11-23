package com.example.aichinese.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.ForgotPasswordRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;
    private final RefreshCookieWriter refreshCookies;

    @PostMapping("/register/student")
    public ApiResponse<LinkTokenResponse> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkRegisterAllowed(clientIpResolver.resolve(httpRequest));
        return ApiResponse.success(
                "Student registered successfully.",
                authService.registerStudent(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        publicAuthRequestLimiter.checkLoginAllowed(clientIp);
        AuthSessionResponse session = authService.login(
                request,
                clientIp,
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return refreshCookies.sessionResponse(httpRequest, "Login successful.", session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        publicAuthRequestLimiter.checkRefreshAllowed(clientIp);
        AuthSessionResponse session = authService.refreshSession(
                refreshCookies.readRefreshToken(httpRequest),
                clientIp,
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return refreshCookies.sessionResponse(httpRequest, "Session refreshed successfully.", session);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Current user retrieved successfully.", authService.getCurrentUser(currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal SecurityUserDetails currentUser, HttpServletRequest httpRequest) {
        authService.logout(currentUser);
        return refreshCookies.clearedCookieResponse(httpRequest, "Logged out successfully.");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkForgotPasswordAllowed(request.email(), clientIpResolver.resolve(httpRequest));
        authService.forgotPassword(request);
        // The same answer whether or not the address has an account.
        return ApiResponse.success("If the email is registered, a password reset link has been sent to it.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        // Same per-IP limit as forgot-password, in a separate bucket.
        publicAuthRequestLimiter.checkResetPasswordAllowed(clientIp);
        authService.resetPassword(request, clientIp);
        return ApiResponse.success("Password reset request processed successfully.", null);
    }
}
