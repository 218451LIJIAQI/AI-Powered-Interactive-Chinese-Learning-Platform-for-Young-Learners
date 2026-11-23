package com.example.aichinese.security;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.ForwardedHeaderProperties;
import com.example.aichinese.util.NetworkAddresses;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    /** Each entry is an address or CIDR range; a host name is rejected at startup. */
    public ClientIpResolver(ForwardedHeaderProperties forwardedHeaderProperties) {
        this.trustedProxies = forwardedHeaderProperties.trustedProxies().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    /**
     * The client address the per-IP rate limits and the refresh-token records use. From a trusted proxy (loopback or
     * a configured one) X-Forwarded-For is read from the right: the first hop that is no trusted proxy is the client,
     * or the left-most hop when all are. Otherwise, or when a hop is no address, it is the connection's peer. The
     * address comes back in one canonical form, so every spelling of it shares a limit.
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        InetAddress remote = NetworkAddresses.parseCanonicalLiteral(remoteAddress);
        if (remote == null) {
            return NetworkAddresses.normalizeHost(remoteAddress);
        }
        if (!isTrustedForwardingProxy(remote)) {
            return remote.getHostAddress();
        }
        // A proxy may add its own header line instead of extending the client's, so every line counts, in order.
        Enumeration<String> forwardedHeaders = request.getHeaders(X_FORWARDED_FOR_HEADER);
        if (forwardedHeaders == null || !forwardedHeaders.hasMoreElements()) {
            return remote.getHostAddress();
        }
        String forwardedHeader = String.join(",", Collections.list(forwardedHeaders));
        if (forwardedHeader.isBlank()) {
            return remote.getHostAddress();
        }
        String[] forwardedHops = forwardedHeader.split(",", -1);
        InetAddress leftMostTrustedHop = remote;
        for (int index = forwardedHops.length - 1; index >= 0; index--) {
            InetAddress hop = NetworkAddresses.parseCanonicalLiteral(forwardedHops[index]);
            if (hop == null) {
                return remote.getHostAddress();
            }
            if (!isTrustedForwardingProxy(hop)) {
                return hop.getHostAddress();
            }
            leftMostTrustedHop = hop;
        }
        return leftMostTrustedHop.getHostAddress();
    }

    private boolean isTrustedForwardingProxy(InetAddress address) {
        return address.isLoopbackAddress()
                || trustedProxies.stream().anyMatch(proxy -> proxy.matches(address.getHostAddress()));
    }
}
