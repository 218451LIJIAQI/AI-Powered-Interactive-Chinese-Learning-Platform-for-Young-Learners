package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.aichinese.config.ForwardedHeaderProperties;

class ClientIpResolverTests {

    @Test
    void trustsLoopbackProxyForLocalDevelopment() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.200");
    }

    @Test
    void trustsExplicitProxyLiteral() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(java.util.List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.200");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustsConfiguredCidrProxyRange() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(java.util.List.of("198.51.100.0/24")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }
}
