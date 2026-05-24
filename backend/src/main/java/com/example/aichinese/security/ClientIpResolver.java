package com.example.aichinese.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.ForwardedHeaderProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP behind a chain of trusted reverse proxies.
 *
 * <p>The platform's audit, rate-limit and lockout layers all need to
 * record / key on the originating client IP so a single user can be
 * throttled or traced across requests. When the deployment runs behind
 * one or more reverse proxies (NGINX, CloudFront, Cloudflare, etc.) the
 * direct {@link HttpServletRequest#getRemoteAddr()} only reports the
 * <em>last hop</em> — every request would look like it came from the
 * proxy. The {@code X-Forwarded-For} header is the standard escape
 * hatch, but it is also trivially spoofable when blindly trusted:
 * a malicious client can send {@code X-Forwarded-For: 1.2.3.4} and
 * masquerade as any IP it likes.</p>
 *
 * <h2>Trust model</h2>
 * <p>The {@code app.forwarded.trusted-proxies} configuration property
 * lists the IPs / CIDR ranges of proxies the application explicitly
 * trusts. {@link #resolve(HttpServletRequest)} only honours the
 * {@code X-Forwarded-For} header when {@code remoteAddr} is one of
 * those trusted addresses (plus the loopback address which is always
 * trusted, so local development through a port-forwarder still works).
 * Untrusted callers get their literal {@code remoteAddr} returned,
 * making header spoofing impossible.</p>
 *
 * <h2>Header parsing</h2>
 * <p>Only the <em>first</em> comma-separated value in
 * {@code X-Forwarded-For} is honoured — that is the client's IP as
 * reported by the first proxy in the chain. Subsequent values record
 * each downstream proxy and are intentionally ignored. IPv6 addresses
 * enclosed in {@code [brackets]} are unwrapped via
 * {@link #normalizeHostLiteral(String)} so comparison against the
 * trust list is consistent regardless of which notation the proxy
 * uses.</p>
 */
@Component
public class ClientIpResolver {

    private final List<CidrRange> trustedProxyCidrs;
    private final List<String> trustedProxyLiterals;

    public ClientIpResolver(ForwardedHeaderProperties forwardedHeaderProperties) {
        this.trustedProxyLiterals = forwardedHeaderProperties.trustedProxies().stream()
                .filter(entry -> !entry.contains("/"))
                .map(this::normalizeHostLiteral)
                .toList();
        this.trustedProxyCidrs = forwardedHeaderProperties.trustedProxies().stream()
                .filter(entry -> entry.contains("/"))
                .map(CidrRange::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        String remoteAddress = normalizeHostLiteral(request.getRemoteAddr());
        String forwardedHeader = request.getHeader("X-Forwarded-For");
        if (isTrustedForwardingProxy(remoteAddress) && forwardedHeader != null && !forwardedHeader.isBlank()) {
            int comma = forwardedHeader.indexOf(',');
            String forwardedClientIp = (comma < 0 ? forwardedHeader : forwardedHeader.substring(0, comma)).trim();
            if (!forwardedClientIp.isBlank()) {
                return forwardedClientIp;
            }
        }
        return remoteAddress;
    }

    boolean isTrustedForwardingProxy(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }

        try {
            InetAddress remoteInetAddress = InetAddress.getByName(remoteAddress);
            if (remoteInetAddress.isLoopbackAddress()) {
                return true;
            }

            String canonicalRemoteAddress = normalizeHostLiteral(remoteInetAddress.getHostAddress());
            if (trustedProxyLiterals.contains(remoteAddress) || trustedProxyLiterals.contains(canonicalRemoteAddress)) {
                return true;
            }

            return trustedProxyCidrs.stream().anyMatch(range -> range.matches(remoteInetAddress));
        } catch (UnknownHostException exception) {
            return trustedProxyLiterals.contains(normalizeHostLiteral(remoteAddress));
        }
    }

    private String normalizeHostLiteral(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private record CidrRange(byte[] network, int prefixLength) {

        static CidrRange parse(String value) {
            String[] parts = value.trim().split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Trusted proxy CIDR must be in address/prefix form: " + value);
            }

            try {
                InetAddress networkAddress = InetAddress.getByName(parts[0].trim());
                int prefixLength = Integer.parseInt(parts[1].trim());
                int maxBits = networkAddress.getAddress().length * 8;
                if (prefixLength < 0 || prefixLength > maxBits) {
                    throw new IllegalArgumentException("Trusted proxy CIDR prefix is out of range: " + value);
                }
                return new CidrRange(mask(networkAddress.getAddress(), prefixLength), prefixLength);
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("Trusted proxy CIDR contains an invalid address: " + value, exception);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Trusted proxy CIDR contains an invalid prefix: " + value, exception);
            }
        }

        boolean matches(InetAddress candidate) {
            byte[] candidateAddress = candidate.getAddress();
            return candidateAddress.length == network.length
                    && Arrays.equals(network, mask(candidateAddress, prefixLength));
        }

        private static byte[] mask(byte[] value, int prefixLength) {
            byte[] masked = value.clone();
            int remainingBits = prefixLength;
            for (int index = 0; index < masked.length; index++) {
                int bitsThisByte = Math.max(0, Math.min(8, remainingBits));
                if (bitsThisByte == 0) {
                    masked[index] = 0;
                } else if (bitsThisByte < 8) {
                    masked[index] = (byte) (masked[index] & (0xFF << (8 - bitsThisByte)));
                }
                remainingBits -= 8;
            }
            return masked;
        }
    }
}
