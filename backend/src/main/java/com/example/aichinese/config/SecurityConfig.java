package com.example.aichinese.config;

import java.util.Objects;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.example.aichinese.security.JwtAccessDeniedHandler;
import com.example.aichinese.security.JwtAuthenticationEntryPoint;
import com.example.aichinese.security.JwtAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * <h2>Filter chain order</h2>
 * <p>{@link RequestIdFilter} is a highest-precedence servlet filter,
 * before Spring Security itself, so even early upload-size rejections
 * carry a correlation id. {@link JwtAuthenticationFilter} is registered
 * only inside the Spring Security chain, before
 * {@link UsernamePasswordAuthenticationFilter}, where it resolves bearer
 * tokens into authenticated principals.</p>
 *
 * <h2>Authorisation policy</h2>
 * <p>Routes are matched in declaration order; the first match wins. The
 * policy is intentionally <em>default-deny</em>: every endpoint that is
 * not explicitly listed in {@code authorizeHttpRequests} requires
 * authentication, so a developer adding a new controller cannot
 * accidentally publish it as public.</p>
 *
 * <p>Public surfaces are kept tightly scoped:</p>
 * <ul>
 *   <li>{@code /api/v1/health} and {@code /actuator/health(/**)} —
 *       container / load-balancer health probes.</li>
 *   <li>{@code /v3/api-docs(/**)} and {@code /swagger-ui(/**)} — OpenAPI
 *       discovery; production has these turned off via
 *       {@code springdoc.api-docs.enabled=false} so the permit-all rule
 *       only kicks in for dev / preview-QA.</li>
 *   <li>Auth bootstrap routes ({@code register}, {@code login},
 *       {@code forgot-password}, {@code reset-password}) and the public
 *       Stripe webhook endpoint.</li>
 *   <li>{@code GET /api/v1/public/**} for the discovery catalog.</li>
 * </ul>
 *
 * <p>Role-scoped routes ({@code /api/v1/students/**},
 * {@code /api/v1/teachers/**}, {@code /api/v1/admin/**}) use the
 * {@code "ROLE_"} prefix that {@link com.example.aichinese.security.SecurityUserDetails}
 * applies; {@code @PreAuthorize} on individual controllers can layer
 * finer-grained checks on top.</p>
 *
 * <h2>Security headers</h2>
 * <ul>
 *   <li>CSRF: disabled — the platform is a stateless JWT API with no
 *       cookie-based session, so the CSRF threat model does not apply.</li>
 *   <li>Sessions: stateless — every request is authenticated via JWT.</li>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} — clickjacking defence.</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} —
 *       trims referrer leakage.</li>
 *   <li>{@code Permissions-Policy} — disables geolocation / camera /
 *       microphone / payment / USB; the platform never asks for any of
 *       these.</li>
 * </ul>
 *
 * <h2>Property bindings</h2>
 * <p>{@code @EnableConfigurationProperties} is co-located here (rather
 * than on individual property classes) so a single annotation lists
 * every {@code @ConfigurationProperties} record / class consumed by the
 * security and platform layer — making it trivial to audit which
 * settings are environment-driven.</p>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        SecurityProperties.class,
        ForwardedHeaderProperties.class,
        UploadProperties.class,
        AiProperties.class,
        PaymentProperties.class,
        EmailProperties.class
})
public class SecurityConfig {

    /**
     * BCrypt cost factor (work factor / log2 of rounds). The Spring default
     * is 10 (\u22481024 rounds); OWASP 2023 guidance recommends \u226512 for
     * new deployments. The encoder transparently verifies older hashes
     * recorded at strength 10, so bumping this is backward-compatible
     * with existing user rows.
     */
    private static final int BCRYPT_STRENGTH = 12;

    /**
     * Permissions-Policy header value. Disables every browser feature the
     * platform never asks for, so a compromised page hosted under the
     * platform's origin cannot prompt the learner for sensitive sensors.
     */
    private static final String PERMISSIONS_POLICY =
            "geolocation=(), microphone=(), camera=(), payment=(), usb=(), "
                    + "accelerometer=(), gyroscope=(), magnetometer=(), serial=()";

    /**
     * Restrictive browser policy for any same-origin UI or API error content
     * served by this application. A frontend served by a separate web server
     * must send the same policy at that delivery edge.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                    + "form-action 'self'; img-src 'self' data: https:; font-src 'self' data:; "
                    + "script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = Objects.requireNonNull(jwtAuthenticationFilter, "jwtAuthenticationFilter");
        this.jwtAuthenticationEntryPoint = Objects.requireNonNull(jwtAuthenticationEntryPoint, "jwtAuthenticationEntryPoint");
        this.jwtAccessDeniedHandler = Objects.requireNonNull(jwtAccessDeniedHandler, "jwtAccessDeniedHandler");
        this.corsConfigurationSource = Objects.requireNonNull(corsConfigurationSource, "corsConfigurationSource");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(opts -> { })
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(ref -> ref.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // HSTS: one year, include subdomains, request preload-list inclusion.
                        // Spring's HstsHeaderWriter only emits this header on HTTPS requests by
                        // default, so dev/preview HTTP traffic stays unaffected.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000L))
                        .permissionsPolicyHeader(perms -> perms.policy(PERMISSIONS_POLICY)))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/health").permitAll()
                        // Actuator probes for Docker / Kubernetes / load-balancer health checks.
                        // Only the curated subset configured under management.endpoints.web.exposure
                        // (health + info) is mounted; everything else is gone from the runtime.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // SpringDoc surfaces: machine-readable OpenAPI JSON and the Swagger UI
                        // bundle. Both are read-only and intentionally accessible without auth so
                        // that the frontend (and any partner integration) can discover the API
                        // contract during development.
                        // PRODUCTION GATE: springdoc.api-docs.enabled and
                        // springdoc.swagger-ui.enabled are both set to false in
                        // application-prod.yml, so these routes return 404 on a prod
                        // deployment and this permit-all rule never applies in practice.
                        // Override via SPRINGDOC_API_DOCS_ENABLED=true if a trusted internal
                        // portal needs schema access in prod.
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register/student",
                                "/api/v1/auth/register/teacher",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/stripe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/ai/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/payments/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/students/**").hasRole("STUDENT")
                        .requestMatchers("/api/v1/teachers/**").hasRole("TEACHER")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Default-deny: any future endpoint that is not explicitly listed
                        // above must be authenticated. This prevents accidentally exposing
                        // a newly-added route as public when the developer forgets to
                        // configure an explicit rule.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
