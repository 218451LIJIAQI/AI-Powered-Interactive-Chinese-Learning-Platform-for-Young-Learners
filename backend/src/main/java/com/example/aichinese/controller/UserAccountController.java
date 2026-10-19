package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.AuthSessionResponse;
import com.example.aichinese.dto.auth.ChangePasswordRequest;
import com.example.aichinese.dto.auth.CurrentUserResponse;
import com.example.aichinese.dto.auth.UpdateProfileRequest;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Account management for every signed-in role (D12, FR-AUTH-12/13). */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserAccountController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityProperties securityProperties;

    public UserAccountController(
            AuthService authService,
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver, "clientIpResolver");
        this.securityProperties = Objects.requireNonNull(securityProperties, "securityProperties");
    }

    @PutMapping("/profile")
    @Operation(summary = "Update my profile", description = "Display name and preferred interface language (en, ms or zh-CN).")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> updateProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Profile updated successfully.", authService.updateProfile(currentUser, request)));
    }

    @PostMapping("/password")
    @Operation(summary = "Change my password", description = "Verifies the current password, revokes every other session and returns a fresh session for this tab.")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        AuthSessionResponse session = authService.changePassword(
                currentUser,
                request,
                clientIpResolver.resolve(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,
                        RefreshCookieWriter.refreshCookie(
                                securityProperties,
                                session.refreshToken().rawToken(),
                                session.refreshToken().ttl()).toString(),
                        RefreshCookieWriter.clearRefreshCookie(
                                securityProperties, RefreshCookieWriter.LEGACY_REFRESH_COOKIE_PATH).toString())
                .body(ApiResponse.success(
                        "Password changed successfully. Other sessions have been signed out.",
                        session.authResponse()));
    }
}
