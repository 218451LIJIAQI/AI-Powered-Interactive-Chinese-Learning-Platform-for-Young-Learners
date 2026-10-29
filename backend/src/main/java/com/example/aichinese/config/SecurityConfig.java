package com.example.aichinese.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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

import com.example.aichinese.security.JwtAuthenticationFilter;
import com.example.aichinese.security.JwtSecurityErrorHandler;
import com.example.aichinese.security.JwtService;

import jakarta.servlet.DispatcherType;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    private static final String PERMISSIONS_POLICY =
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), "
                    + "accelerometer=(), gyroscope=(), magnetometer=(), serial=()";

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                    + "form-action 'self'; img-src 'self' data: https:; font-src 'self' data:; "
                    + "script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'";

    private static final String[] API_DOC_ENDPOINTS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };
    /** Also the endpoints whose validation errors are answered without field errors (GlobalExceptionHandler). */
    public static final List<String> PUBLIC_AUTH_POST_ENDPOINTS = List.of(
            "/api/v1/auth/register/student",
            "/api/v1/auth/register/teacher",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/reset-password/check",
            "/api/v1/auth/confirm-email",
            "/api/v1/auth/confirm-signup-email",
            "/api/v1/auth/admin-invite/check",
            "/api/v1/auth/admin-invite/accept",
            "/api/v1/public/support");

    /** Keep the name corsConfigurationSource: it picks CorsConfig's bean over the MVC HandlerMappingIntrospector. */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserDetailsService userDetailsService,
            JwtSecurityErrorHandler jwtSecurityErrorHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
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
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(API_DOC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POST_ENDPOINTS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        // Account management (profile, change password) for every signed-in role (FRAUTH07-08).
                        .requestMatchers("/api/v1/users/me/**").authenticated()
                        // Learner AI tools stay student-only; approved teachers get unsaved AI drafts under
                        // /api/v1/teachers/ai/** (FRAI05), covered by the TEACHER rule below plus the
                        // ApprovedTeacherInterceptor status guard (FRTVER04).
                        .requestMatchers("/api/v1/ai/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/students/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/teachers/**").hasRole("TEACHER")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
