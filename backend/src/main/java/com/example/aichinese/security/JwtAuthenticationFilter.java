package com.example.aichinese.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request filter that establishes a Spring Security
 * {@link SecurityContextHolder} authentication from a bearer JWT.
 *
 * <h2>Failure-handling philosophy</h2>
 * <p>This filter never aborts the request chain on a bad token — it
 * simply continues without setting an authentication, leaving the
 * downstream security configuration to decide whether the missing
 * authentication is fatal (it usually is, via the
 * {@link JwtAuthenticationEntryPoint}, but some endpoints are
 * intentionally anonymous-accessible). The reasoning:</p>
 * <ul>
 *   <li>Throwing here would short-circuit Spring's well-tested
 *       challenge-response machinery and produce inconsistent error
 *       shapes.</li>
 *   <li>Expired tokens are the overwhelmingly common case and are not
 *       interesting enough to log at WARN — see the {@code DEBUG} log
 *       calls inside the filter for the rationale.</li>
 * </ul>
 *
 * <h2>Session invalidation</h2>
 * <p>{@link #isTokenValidForCurrentSession(String, UserDetails)}
 * compares the {@code sessionVersion} encoded in the token against the
 * value currently stored on the user row. Bumping
 * {@code User.sessionVersion} (on password change, admin-driven
 * deactivation, or explicit "log out everywhere") invalidates every
 * outstanding token for that user in a single step — without
 * maintaining a server-side token revocation list.</p>
 *
 * <h2>Log hygiene</h2>
 * <p>Token contents are never logged; only the exception class name
 * for a parse failure. This prevents an attacker who can read logs
 * from harvesting valid tokens, and keeps PII (the user's email
 * encoded in the JWT subject) out of disk-resident log files.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;

        try {
            email = jwtService.extractUsername(token);
        } catch (Exception exception) {
            // Common cases here are expired tokens, malformed tokens, signature
            // mismatches, and tokens signed with a rotated secret. Log at DEBUG
            // so legitimate users with expired sessions do not flood WARN logs,
            // but at WARN if it looks like an actively-malicious token (header
            // present but parse failed for a non-trivial reason). We only log
            // the exception class to avoid leaking the token contents.
            log.debug("Rejecting unparsable bearer token: {}", exception.getClass().getSimpleName());
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(email) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException notFound) {
                // Token signature was valid but the user has since been deleted
                // or deactivated. Continue unauthenticated rather than 500ing.
                log.debug("Token references an unknown principal; continuing unauthenticated.");
                filterChain.doFilter(request, response);
                return;
            }
            if (userDetails.isEnabled()
                    && userDetails.isAccountNonExpired()
                    && userDetails.isAccountNonLocked()
                    && userDetails.isCredentialsNonExpired()
                    && isTokenValidForCurrentSession(token, userDetails)) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenValidForCurrentSession(String token, UserDetails userDetails) {
        if (userDetails instanceof SecurityUserDetails securityUserDetails) {
            return jwtService.isTokenValid(
                    token,
                    securityUserDetails.getUsername(),
                    securityUserDetails.getUser().getSessionVersion());
        }

        return jwtService.isTokenValid(token, userDetails.getUsername(), 0L);
    }
}
