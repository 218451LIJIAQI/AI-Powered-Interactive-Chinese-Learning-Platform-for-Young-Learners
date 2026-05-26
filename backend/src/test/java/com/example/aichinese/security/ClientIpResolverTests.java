package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.aichinese.config.ForwardedHeaderProperties;

/**
 * Unit coverage for {@code ClientIpResolver} - pins the trusted-proxy
 * model: loopback / configured CIDR proxies are honoured (X-Forwarded-For
 * is parsed), untrusted proxies are ignored (remote-addr wins), and the
 * left-most non-trusted hop is selected so attackers cannot spoof the
 * client IP by injecting their own forwarded header.
 */
class ClientIpResolverTests {

    @Test
    void trustsLoopbackProxyForLocalDevelopment() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 127.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.200");
    }

    @Test
    void trustsExplicitProxyLiteral() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.200");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustsConfiguredCidrProxyRange() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.0/24")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void rejectsHostnameBasedTrustedProxyCidrEntries() {
        assertThatThrownBy(() -> new ClientIpResolver(new ForwardedHeaderProperties(List.of("proxy.internal/24"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trusted proxy CIDR contains an invalid address");
    }

    @Test
    void rejectsHostnameBasedTrustedProxyLiteralEntries() {
        assertThatThrownBy(() -> new ClientIpResolver(new ForwardedHeaderProperties(List.of("proxy.internal"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trusted proxy entry contains an invalid address");
    }

    @Test
    void selectsRealClientWhenTrustedProxyAppendsAfterSpoofedForwardedValue() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "192.0.2.44, 203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresNonAddressSpoofBeforeRealClientAddress() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "spoofed.example, 203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToTrustedPeerWhenNearestForwardedHopIsNotAnAddress() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.0.113.9, not-an-address");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.200");
    }

    @Test
    void canonicalizesEquivalentClientAddressFormsForStableRateLimitKeys() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("198.51.100.200")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.200");
        request.addHeader("X-Forwarded-For", "203.000.113.009");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustsEquivalentIpv6LiteralFormsForConfiguredProxy() {
        ClientIpResolver resolver = new ClientIpResolver(new ForwardedHeaderProperties(List.of("2001:db8::10")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8:0:0:0:0:0:10");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }
}
