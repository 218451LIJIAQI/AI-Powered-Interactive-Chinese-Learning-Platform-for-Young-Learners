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
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/student")
    public ApiResponse<RegistrationResponse> registerStudent(@Valid @RequestBody RegisterStudentRequest request) {
        return ApiResponse.success(
                "Student registered successfully.",
                authService.registerStudent(request));
    }

    @PostMapping("/register/teacher")
    public ApiResponse<RegistrationResponse> registerTeacher(@Valid @RequestBody RegisterTeacherRequest request) {
        return ApiResponse.success(
                "Teacher registered successfully.",
                authService.registerTeacher(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success("Login successful.", authService.login(request, resolveClientIp(httpRequest)));
    }

    /**
     * Resolves the originating client IP. Honours the standard X-Forwarded-For
     * header so the rate limiter still keys correctly when the API runs behind
     * a reverse proxy / load balancer in deployment, while falling back to the
     * direct remote address for local development.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For is a comma-separated chain; the first entry is
            // the original client. Trim aggressively against header whitespace.
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
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
    public ApiResponse<Void> logout() {
        return ApiResponse.success("Logged out successfully.", null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.success(
                "If the email is registered, a password reset instruction will be sent once email delivery is configured.",
                null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        // Defensive return: the service intentionally throws SERVICE_UNAVAILABLE for this prototype.
        return ApiResponse.success("Password reset request processed successfully.", null);
    }
}
