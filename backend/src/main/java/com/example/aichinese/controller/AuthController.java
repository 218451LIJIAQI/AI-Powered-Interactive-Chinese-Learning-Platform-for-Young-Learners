package com.example.aichinese.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
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
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.RegisterTeacherRequest;
import com.example.aichinese.dto.auth.RegistrationResponse;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;

    public AuthController(
            AuthService authService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties) {
        this.authService = authService;
        this.publicAuthRequestLimiter = publicAuthRequestLimiter;
        this.clientIpResolver = clientIpResolver;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/register/student")
    public ApiResponse<RegistrationResponse> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request,
            HttpServletRequest httpRequest) {
        checkRegisterAllowed(httpRequest);
        return ApiResponse.success(
                "Student registered successfully.",
                authService.registerStudent(request));
    }

    @PostMapping("/register/teacher")
    public ApiResponse<RegistrationResponse> registerTeacher(
            @Valid @RequestBody RegisterTeacherRequest request,
            HttpServletRequest httpRequest) {
        checkRegisterAllowed(httpRequest);
        return ApiResponse.success(
                "Teacher registered successfully.",
                authService.registerTeacher(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        AuthSessionResponse session = authService.login(
                request,
                clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return RefreshCookieWriter.sessionResponse(securityProperties, "Login successful.", session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "${app.security.jwt.refresh-token-cookie-name:mandarin_spark_refresh}", required = false)
            String refreshToken,
            HttpServletRequest httpRequest) {
        AuthSessionResponse session = authService.refreshSession(
                refreshToken,
                clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return RefreshCookieWriter.sessionResponse(securityProperties, "Session refreshed successfully.", session);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Current user retrieved successfully.", authService.getCurrentUser(currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        authService.logout(currentUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookieWriter.clearedCookieHeaders(securityProperties))
                .body(ApiResponse.success("Logged out successfully.", null));
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
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        // Same per-IP limit as forgot-password, in a separate bucket.
        publicAuthRequestLimiter.checkResetPasswordAllowed(clientIpResolver.resolve(httpRequest));
        authService.resetPassword(request);
        return ApiResponse.success("Password reset request processed successfully.", null);
    }

    private void checkRegisterAllowed(HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkRegisterAllowed(clientIpResolver.resolve(httpRequest));
    }
}
