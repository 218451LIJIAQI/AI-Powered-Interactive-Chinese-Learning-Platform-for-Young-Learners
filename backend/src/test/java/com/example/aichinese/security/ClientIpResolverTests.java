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

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String LOOPBACK_PROXY = "127.0.0.1";
    private static final String PROXY_IP = "198.51.100.200";
    private static final String TRUSTED_PROXY_CIDR = "198.51.100.0/24";
    private static final String TRUSTED_IPV6_PROXY = "2001:db8::10";
    private static final String EQUIVALENT_TRUSTED_IPV6_PROXY = "2001:db8:0:0:0:0:0:10";
    private static final String LOCAL_CLIENT_IP = "198.51.100.10";
    private static final String CLIENT_IP = "203.0.113.9";
    private static final String SECOND_CLIENT_IP = "203.0.113.10";
    private static final String SPOOFED_IP = "192.0.2.44";
    private static final String HOSTNAME_PROXY = "proxy.internal";
    private static final String INVALID_CIDR_MESSAGE = "Trusted proxy CIDR contains an invalid address";
    private static final String INVALID_LITERAL_MESSAGE = "Trusted proxy entry contains an invalid address";

    @Test
    void trustsLoopbackProxyForLocalDevelopment() {
        ClientIpResolver resolver = resolver();
        MockHttpServletRequest request = request(LOOPBACK_PROXY, LOCAL_CLIENT_IP + ", " + LOOPBACK_PROXY);

        assertThat(resolver.resolve(request)).isEqualTo(LOCAL_CLIENT_IP);
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedProxy() {
        ClientIpResolver resolver = resolver();
        MockHttpServletRequest request = request(PROXY_IP, CLIENT_IP);

        assertThat(resolver.resolve(request)).isEqualTo(PROXY_IP);
    }

    @Test
    void trustsExplicitProxyLiteral() {
        ClientIpResolver resolver = resolver(PROXY_IP);
        MockHttpServletRequest request = request(PROXY_IP, CLIENT_IP + ", " + PROXY_IP);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT_IP);
    }

    @Test
    void trustsConfiguredCidrProxyRange() {
        ClientIpResolver resolver = resolver(TRUSTED_PROXY_CIDR);
        MockHttpServletRequest request = request(PROXY_IP, SECOND_CLIENT_IP);

        assertThat(resolver.resolve(request)).isEqualTo(SECOND_CLIENT_IP);
    }

    @Test
    void rejectsHostnameBasedTrustedProxyCidrEntries() {
        assertThatThrownBy(() -> resolver(HOSTNAME_PROXY + "/24"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(INVALID_CIDR_MESSAGE);
    }

    @Test
    void rejectsHostnameBasedTrustedProxyLiteralEntries() {
        assertThatThrownBy(() -> resolver(HOSTNAME_PROXY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(INVALID_LITERAL_MESSAGE);
    }

    @Test
    void selectsRealClientWhenTrustedProxyAppendsAfterSpoofedForwardedValue() {
        ClientIpResolver resolver = resolver(PROXY_IP);
        MockHttpServletRequest request = request(PROXY_IP, SPOOFED_IP + ", " + CLIENT_IP);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT_IP);
    }

    @Test
    void ignoresNonAddressSpoofBeforeRealClientAddress() {
        ClientIpResolver resolver = resolver(PROXY_IP);
        MockHttpServletRequest request = request(PROXY_IP, "spoofed.example, " + CLIENT_IP);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT_IP);
    }

    @Test
    void fallsBackToTrustedPeerWhenNearestForwardedHopIsNotAnAddress() {
        ClientIpResolver resolver = resolver(PROXY_IP);
        MockHttpServletRequest request = request(PROXY_IP, CLIENT_IP + ", not-an-address");

        assertThat(resolver.resolve(request)).isEqualTo(PROXY_IP);
    }

    @Test
    void canonicalizesEquivalentClientAddressFormsForStableRateLimitKeys() {
        ClientIpResolver resolver = resolver(PROXY_IP);
        MockHttpServletRequest request = request(PROXY_IP, "203.000.113.009");

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT_IP);
    }

    @Test
    void trustsEquivalentIpv6LiteralFormsForConfiguredProxy() {
        ClientIpResolver resolver = resolver(TRUSTED_IPV6_PROXY);
        MockHttpServletRequest request = request(EQUIVALENT_TRUSTED_IPV6_PROXY, CLIENT_IP);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT_IP);
    }

    private static ClientIpResolver resolver(String... trustedProxies) {
        return new ClientIpResolver(new ForwardedHeaderProperties(List.of(trustedProxies)));
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader(X_FORWARDED_FOR, forwardedFor);
        return request;
    }
}
