package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import com.example.aichinese.security.JwtAccessDeniedHandler;
import com.example.aichinese.security.JwtAuthenticationEntryPoint;
import com.example.aichinese.security.JwtAuthenticationFilter;

class SecurityConfigTests {

    @Test
    void jwtFilterServletAutoRegistrationIsDisabledBecauseSecurityChainOwnsIt() {
        JwtAuthenticationFilter jwtFilter = mock(JwtAuthenticationFilter.class);

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                securityConfig(jwtFilter).jwtAuthenticationFilterRegistration(jwtFilter);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isSameAs(jwtFilter);
    }

    private static SecurityConfig securityConfig(JwtAuthenticationFilter jwtFilter) {
        return new SecurityConfig(
                jwtFilter,
                mock(JwtAuthenticationEntryPoint.class),
                mock(JwtAccessDeniedHandler.class),
                request -> null);
    }
}
