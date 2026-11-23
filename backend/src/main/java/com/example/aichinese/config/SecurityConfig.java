package com.example.aichinese.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.example.aichinese.security.JwtAuthenticationFilter;
import com.example.aichinese.security.JwtSecurityErrorHandler;
import com.example.aichinese.security.JwtService;
import com.example.aichinese.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;
    static final String HEALTH_PATH = "/api/v1/health";
    static final String PUBLIC_API_ROOT = "/api/v1/public";

    private static final String PERMISSIONS_POLICY =
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), "
                    + "accelerometer=(), gyroscope=(), magnetometer=(), serial=()";

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                    + "form-action 'self'; img-src 'self' data: https:; font-src 'self' data:; "
                    + "script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'";

    private static final String[] API_DOC_ENDPOINTS = {
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };
    /**
     * The POST endpoints that need no access token: sign-up, sign-in, the session refresh, the account links and the
     * Help page form. Also the endpoints whose validation errors are answered without field errors
     * (GlobalExceptionHandler).
     */
    public static final List<String> PUBLIC_POST_ENDPOINTS = List.of(
            "/api/v1/auth/register/student",
            "/api/v1/auth/login",
            RefreshCookieOriginFilter.REFRESH_PATH,
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/reset-password/check",
            "/api/v1/auth/confirm-email",
            "/api/v1/auth/confirm-signup-email",
            "/api/v1/auth/admin-invite/check",
            "/api/v1/auth/admin-invite/accept",
            PUBLIC_API_ROOT + "/support");

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserDetailsService userDetailsService,
            RefreshTokenService refreshTokenService,
            JwtSecurityErrorHandler jwtSecurityErrorHandler,
            ObjectMapper objectMapper,
            CorsConfig.CorsAllowedOrigins corsAllowedOrigins,
            // Keep this parameter name: it picks CorsConfig's bean over the MVC HandlerMappingIntrospector.
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // X-Content-Type-Options: nosniff and X-Frame-Options: DENY are Spring Security's defaults and stay on.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000L))
                        .permissionsPolicyHeader(perms -> perms.policy(PERMISSIONS_POLICY)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtSecurityErrorHandler)
                        .accessDeniedHandler(jwtSecurityErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HEALTH_PATH).permitAll()
                        .requestMatchers(API_DOC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_API_ROOT + "/**").permitAll()
                        // Learner AI tools stay learner-only; the admins' AI quiz draft is under /api/v1/admin/ai/**.
                        .requestMatchers("/api/v1/ai/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/students/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Everything else is open to every signed-in role: the session (/auth/me, /auth/logout),
                        // notifications and account management under /users/me (FRAUTH07-08).
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new RefreshCookieOriginFilter(objectMapper, corsAllowedOrigins.exact()),
                        CorsFilter.class)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userDetailsService, refreshTokenService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
