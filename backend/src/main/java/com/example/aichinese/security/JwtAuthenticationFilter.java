package com.example.aichinese.security;

import java.io.IOException;
import java.util.Objects;

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

    /**
     * RFC 6750 \u00a72.1 mandates the bearer scheme name be matched
     * case-sensitively (i.e. "Bearer" \u2014 not "bearer" or "BEARER"),
     * and the single space separator is the only allowed whitespace
     * between the scheme and the credential. We deliberately match this
     * literal prefix rather than parsing more permissively, so that
     * clients which send malformed headers fail closed.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Length of {@link #BEARER_PREFIX} \u2014 cached to avoid repeated length() calls in the hot path. */
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /** Default session version for legacy / non-platform {@link UserDetails} that lack the field. */
    private static final long DEFAULT_SESSION_VERSION = 0L;

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.userDetailsService = Objects.requireNonNull(userDetailsService, "userDetailsService");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX_LENGTH);
        if (!StringUtils.hasText(token)) {
            // Header was literally "Bearer   " with no actual credential. Skip
            // the JwtService cost and continue unauthenticated.
            filterChain.doFilter(request, response);
            return;
        }

        String email;
        try {
            email = jwtService.extractUsername(token);
        } catch (Exception exception) {
            // Common cases here are expired tokens, malformed tokens, signature
            // mismatches, and tokens signed with a rotated secret. Log at DEBUG
            // so legitimate users with expired sessions do not flood WARN logs.
            // We only log the exception class to avoid leaking the token contents.
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

    /**
     * Validates the token against the {@link UserDetails} principal, including
     * a {@code sessionVersion} round-trip when the principal is the platform's
     * own {@link SecurityUserDetails}. Non-platform principals fall back to a
     * version-less validation \u2014 they should never appear in production,
     * but the fallback exists so the filter is reusable in tests that mock
     * {@link UserDetailsService}.
     */
    private boolean isTokenValidForCurrentSession(@NonNull String token, @NonNull UserDetails userDetails) {
        if (userDetails instanceof SecurityUserDetails securityUserDetails) {
            return jwtService.isTokenValid(
                    token,
                    securityUserDetails.getUsername(),
                    securityUserDetails.getUser().getSessionVersion());
        }

        return jwtService.isTokenValid(token, userDetails.getUsername(), DEFAULT_SESSION_VERSION);
    }
}
