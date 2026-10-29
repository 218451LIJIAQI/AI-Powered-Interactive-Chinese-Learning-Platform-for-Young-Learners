package com.example.aichinese.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /** An empty trusted proxy column configures none (loopback is always trusted). */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            trusts the loopback proxy for local development         |                |127.0.0.1             |198.51.100.10, 127.0.0.1    |198.51.100.10
            ignores the forwarded header of an untrusted proxy      |                |198.51.100.200        |203.0.113.9                 |198.51.100.200
            trusts an explicit proxy literal                        |198.51.100.200  |198.51.100.200        |203.0.113.9, 198.51.100.200 |203.0.113.9
            trusts a configured CIDR proxy range                    |198.51.100.0/24 |198.51.100.200        |203.0.113.10                |203.0.113.10
            selects the real client after a spoofed forwarded value |198.51.100.200  |198.51.100.200        |192.0.2.44, 203.0.113.9     |203.0.113.9
            ignores a non-address spoof before the real client      |198.51.100.200  |198.51.100.200        |spoofed.example, 203.0.113.9|203.0.113.9
            falls back to the peer when the nearest hop is no address|198.51.100.200 |198.51.100.200        |203.0.113.9, not-an-address |198.51.100.200
            canonicalizes equivalent client forms for rate limits   |198.51.100.200  |198.51.100.200        |203.000.113.009             |203.0.113.9
            trusts equivalent IPv6 forms of a configured proxy      |2001:db8::10    |2001:db8:0:0:0:0:0:10 |203.0.113.9                 |203.0.113.9
            """)
    void resolvesTheClientAddressThroughTrustedProxiesOnly(
            String scenario, String trustedProxy, String remoteAddr, String forwardedFor, String expectedClientIp) {
        ClientIpResolver resolver = trustedProxy == null ? resolver() : resolver(trustedProxy);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);

        assertThat(resolver.resolve(request)).isEqualTo(expectedClientIp);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            proxy.internal/24 | Trusted proxy CIDR contains an invalid address
            proxy.internal    | Trusted proxy entry contains an invalid address
            """)
    void rejectsHostnameBasedTrustedProxyEntries(String trustedProxy, String expectedMessage) {
        assertThatThrownBy(() -> resolver(trustedProxy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static ClientIpResolver resolver(String... trustedProxies) {
        return new ClientIpResolver(new ForwardedHeaderProperties(List.of(trustedProxies)));
    }
}
