package com.example.aichinese.controller;

import java.time.Duration;
import java.util.Objects;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
import com.example.aichinese.dto.auth.RefreshTokenIssue;
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

    private static final String LEGACY_REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    private final AuthService authService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;

    public AuthController(
            AuthService authService,
            PublicAuthRequestLimiter publicAuthRequestLimiter,
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.publicAuthRequestLimiter = Objects.requireNonNull(
                publicAuthRequestLimiter,
                "publicAuthRequestLimiter");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties");
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
        return withRefreshCookie("Login successful.", session.authResponse(), session.refreshToken());
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
        return withRefreshCookie("Session refreshed successfully.", session.authResponse(), session.refreshToken());
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Current user retrieved successfully.",
                        authService.getCurrentUser(currentUser)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        authService.logout(currentUser);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        clearRefreshCookie(REFRESH_COOKIE_PATH).toString(),
                        clearRefreshCookie(LEGACY_REFRESH_COOKIE_PATH).toString())
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
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("Password reset request processed successfully.", null);
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(
            String message,
            AuthResponse authResponse,
            RefreshTokenIssue refreshToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(refreshToken.rawToken(), refreshToken.ttl()).toString(),
                        clearRefreshCookie(LEGACY_REFRESH_COOKIE_PATH).toString())
                .body(ApiResponse.success(message, authResponse));
    }

    private ResponseCookie refreshCookie(String rawToken, Duration maxAge) {
        return refreshCookieBuilder(rawToken, REFRESH_COOKIE_PATH).maxAge(maxAge).build();
    }

    private ResponseCookie clearRefreshCookie(String path) {
        return refreshCookieBuilder("", path).maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookieBuilder(String value, String path) {
        return ResponseCookie.from(securityProperties.refreshTokenCookieName(), value)
                .httpOnly(true)
                .secure(securityProperties.refreshTokenCookieSecure())
                .sameSite(securityProperties.refreshTokenSameSite())
                .path(path);
    }

    private void checkRegisterAllowed(HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkRegisterAllowed(clientIpResolver.resolve(httpRequest));
    }
}
