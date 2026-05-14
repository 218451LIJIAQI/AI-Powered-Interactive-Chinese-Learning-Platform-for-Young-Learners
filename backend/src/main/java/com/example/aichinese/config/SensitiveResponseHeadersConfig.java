package com.example.aichinese.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds {@code Cache-Control: no-store} (plus the older {@code Pragma: no-cache}
 * companion) to API responses that contain personally-identifiable or
 * privileged data, so that:
 *
 * <ul>
 *   <li>The browser disk cache and bfcache do not retain the JSON body of
 *       {@code /auth/me} or {@code /students/payments/**}, preventing a
 *       logged-out user from replaying it via the back button.</li>
 *   <li>Shared/proxy caches that ignore the JWT-only auth path do not
 *       accidentally serve another tenant's payment data.</li>
 * </ul>
 *
 * Using a {@link WebMvcConfigurer} interceptor (instead of inline
 * {@code ResponseEntity.ok().cacheControl(...)} on every controller) keeps
 * the policy declarative and prevents new PII endpoints from silently
 * shipping without the header. {@code AuthController#getCurrentUser} also
 * sets the header at the controller level for defense in depth — both
 * layers writing the same value is safe because Spring de-duplicates
 * Cache-Control on the response.
 */
@Configuration
public class SensitiveResponseHeadersConfig implements WebMvcConfigurer {

    /**
     * Path patterns whose responses must never be cached. Keep this list
     * narrow on purpose — applying {@code no-store} broadly would
     * defeat the {@code /ai/availability} short-cache and would also
     * stop static-public surfaces ({@code /public/**},
     * {@code /subscription-plans/**}) from being CDN-friendly.
     */
    private static final String[] NO_STORE_PATTERNS = {
            "/api/v1/auth/me",
            "/api/v1/students/payments/**",
            "/api/v1/students/refund-requests/**",
            "/api/v1/payments/**",
            "/api/v1/admin/audit-logs/**"
    };

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(
                    @NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull Object handler,
                    org.springframework.web.servlet.ModelAndView modelAndView) {
                // postHandle runs before the response is committed, which is
                // when Spring still allows header mutation. Use setHeader
                // (not addHeader) so we never produce a duplicated header
                // line if another component already wrote one.
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
            }
        }).addPathPatterns(NO_STORE_PATTERNS);
    }
}
