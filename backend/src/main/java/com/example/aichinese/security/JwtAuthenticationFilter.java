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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.service.RefreshTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    static String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        String email;
        String sessionId;
        boolean persistent;
        try {
            claims = jwtService.extractAllClaims(token);
            email = claims.getSubject();
            sessionId = jwtService.sessionId(claims);
            persistent = jwtService.isPersistentSession(claims);
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Rejecting unparsable bearer token: {}", exception.getClass().getSimpleName());
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(email) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException notFound) {
                log.debug("Token references an unknown principal; continuing unauthenticated.");
                filterChain.doFilter(request, response);
                return;
            }
            // Only SecurityUserDetails carries the session version that revokes old tokens (SDD 3.4); fail closed otherwise.
            // A token of a browser that signed out (its session ended) is refused; one without a session predates them.
            if (userDetails instanceof SecurityUserDetails account
                    && account.isEnabled()
                    && jwtService.isTokenValid(claims, account.getUsername(), account.getSessionVersion())
                    && (sessionId == null || refreshTokenService.isSessionActive(account.getUserId(), sessionId))) {
                SecurityUserDetails principal = account.withSession(persistent, sessionId);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
