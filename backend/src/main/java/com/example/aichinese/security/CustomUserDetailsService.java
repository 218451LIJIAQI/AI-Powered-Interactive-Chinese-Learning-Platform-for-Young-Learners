package com.example.aichinese.security;

import java.util.Objects;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.repository.UserRepository;

/**
 * Spring Security {@link UserDetailsService} adapter that resolves a
 * platform {@link com.example.aichinese.entity.User} into the
 * {@link SecurityUserDetails} wrapper consumed by the authentication
 * filter chain.
 *
 * <p>The lookup is delegated to {@link UserRepository#findByEmailWithRoles(String)}
 * which performs a case-insensitive match (matching the case-insensitive
 * uniqueness contract on the {@code email} column) and eagerly loads
 * {@code roles} in a single round-trip. Eager-fetching here is essential
 * because the granted-authority list is computed inside the JWT filter
 * before the persistence context is closed, so a lazy roles association
 * would either fail or trigger a separate SELECT per request.</p>
 *
 * <p>The thrown {@link UsernameNotFoundException} carries a generic
 * {@code "User not found"} message that Spring Security never surfaces
 * to the client — the authentication entry point always returns the
 * canonical "session expired / not signed in" message, so the
 * distinction between "wrong email" and "wrong password" cannot be
 * mined from response bodies.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    }

    /**
     * Resolves a {@link SecurityUserDetails} for the supplied email
     * (Spring Security calls this "username"). A {@code null} or blank
     * argument is rejected with the same {@link UsernameNotFoundException}
     * as a missing row so the auth entry point never has to differentiate
     * between malformed-input and not-found cases.
     *
     * <p>Marked {@link Transactional @Transactional(readOnly = true)} so
     * the eager role fetch inside
     * {@link UserRepository#findByEmailWithRoles(String)} runs in a
     * managed read-only transaction; this both lets Hibernate skip
     * dirty-checking on the loaded entities and pins the connection
     * to a follower replica when one is configured.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("User not found");
        }
        return userRepository.findByEmailWithRoles(username)
                .map(SecurityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
