package com.example.aichinese.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.ForwardedHeaderProperties;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<CidrRange> trustedProxyCidrs;
    private final List<String> trustedProxyLiterals;

    public ClientIpResolver(ForwardedHeaderProperties forwardedHeaderProperties) {
        Objects.requireNonNull(forwardedHeaderProperties, "forwardedHeaderProperties");
        this.trustedProxyLiterals = forwardedHeaderProperties.trustedProxies().stream()
                .filter(entry -> !entry.contains("/"))
                .map(ClientIpResolver::parseTrustedProxyLiteral)
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

        String remoteAddress = canonicalNumericAddressOrLiteral(request.getRemoteAddr());
        String forwardedHeader = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (isTrustedForwardingProxy(remoteAddress) && forwardedHeader != null && !forwardedHeader.isBlank()) {
            String[] forwardedHops = forwardedHeader.split(",", -1);
            String leftMostTrustedHop = remoteAddress;
            for (int index = forwardedHops.length - 1; index >= 0; index--) {
                String candidate = normalizeHostLiteral(forwardedHops[index]);
                InetAddress parsedCandidate = parseNumericAddress(candidate);
                if (parsedCandidate == null) {
                    return remoteAddress;
                }
                candidate = canonicalNumericAddressOrLiteral(candidate);
                leftMostTrustedHop = candidate;
                if (!isTrustedForwardingProxy(candidate)) {
                    return candidate;
                }
            }
            return leftMostTrustedHop;
        }
        return remoteAddress;
    }

    private boolean isTrustedForwardingProxy(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return false;
        }

        InetAddress remoteInetAddress = parseNumericAddress(remoteAddress);
        if (remoteInetAddress == null) {
            return false;
        }
        if (remoteInetAddress.isLoopbackAddress()) {
            return true;
        }

        String canonicalRemoteAddress = normalizeHostLiteral(remoteInetAddress.getHostAddress());
        if (trustedProxyLiterals.contains(remoteAddress) || trustedProxyLiterals.contains(canonicalRemoteAddress)) {
            return true;
        }

        return trustedProxyCidrs.stream().anyMatch(range -> range.matches(remoteInetAddress));
    }

    private static String normalizeHostLiteral(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private static String canonicalNumericAddressOrLiteral(String value) {
        String normalized = normalizeHostLiteral(value);
        InetAddress parsedAddress = parseNumericAddress(normalized);
        return parsedAddress == null
                ? normalized
                : normalizeHostLiteral(parsedAddress.getHostAddress());
    }

    private static String parseTrustedProxyLiteral(String value) {
        String normalized = normalizeHostLiteral(value);
        if (normalized.isEmpty()) {
            return "";
        }
        InetAddress parsedAddress = parseNumericAddress(normalized);
        if (parsedAddress == null) {
            throw new IllegalArgumentException("Trusted proxy entry contains an invalid address: " + value);
        }
        return normalizeHostLiteral(parsedAddress.getHostAddress());
    }

    private static InetAddress parseNumericAddress(String value) {
        String normalized = normalizeHostLiteral(value);
        try {
            String[] ipv4Parts = normalized.split("\\.", -1);
            if (ipv4Parts.length == 4) {
                byte[] address = new byte[4];
                for (int index = 0; index < ipv4Parts.length; index++) {
                    if (ipv4Parts[index].isBlank() || !ipv4Parts[index].chars().allMatch(Character::isDigit)) {
                        return null;
                    }
                    int octet = Integer.parseInt(ipv4Parts[index]);
                    if (octet < 0 || octet > 255) {
                        return null;
                    }
                    address[index] = (byte) octet;
                }
                return InetAddress.getByAddress(address);
            }
            if (!normalized.contains(":")
                    || normalized.contains("%")
                    || !normalized.matches("[0-9a-f:.]+")) {
                return null;
            }
            return InetAddress.getByName(normalized);
        } catch (UnknownHostException | NumberFormatException exception) {
            return null;
        }
    }

    private record CidrRange(byte[] network, int prefixLength) {

        static CidrRange parse(String value) {
            String[] parts = value.strip().split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Trusted proxy CIDR must be in address/prefix form: " + value);
            }

            try {
                InetAddress networkAddress = parseNumericAddress(parts[0].strip());
                if (networkAddress == null) {
                    throw new IllegalArgumentException("Trusted proxy CIDR contains an invalid address: " + value);
                }
                int prefixLength = Integer.parseInt(parts[1].strip());
                int maxBits = networkAddress.getAddress().length * 8;
                if (prefixLength < 0 || prefixLength > maxBits) {
                    throw new IllegalArgumentException("Trusted proxy CIDR prefix is out of range: " + value);
                }
                return new CidrRange(mask(networkAddress.getAddress(), prefixLength), prefixLength);
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
