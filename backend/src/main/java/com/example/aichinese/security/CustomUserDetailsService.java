package com.example.aichinese.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailWithRoles(username)
                .map(SecurityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
