package com.example.aichinese.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.ForwardedHeaderProperties;
import com.example.aichinese.config.HttpUrlValidation;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final List<CidrRange> trustedProxyCidrs;
    private final List<String> trustedProxyLiterals;

    public ClientIpResolver(ForwardedHeaderProperties forwardedHeaderProperties) {
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

        String remoteAddress = HttpUrlValidation.normalizeHost(request.getRemoteAddr());
        InetAddress remote = parseNumericAddress(remoteAddress);
        if (remote == null) {
            return remoteAddress;
        }
        String forwardedHeader = request.getHeader(X_FORWARDED_FOR_HEADER);
        if (!isTrustedForwardingProxy(remote) || forwardedHeader == null || forwardedHeader.isBlank()) {
            return canonical(remote);
        }
        String[] forwardedHops = forwardedHeader.split(",", -1);
        InetAddress leftMostTrustedHop = remote;
        for (int index = forwardedHops.length - 1; index >= 0; index--) {
            // Normalised here and again inside parseNumericAddress, so "[ 203.0.113.9 ]" still counts as an address.
            InetAddress hop = parseNumericAddress(HttpUrlValidation.normalizeHost(forwardedHops[index]));
            if (hop == null) {
                return canonical(remote);
            }
            if (!isTrustedForwardingProxy(hop)) {
                return canonical(hop);
            }
            leftMostTrustedHop = hop;
        }
        return canonical(leftMostTrustedHop);
    }

    private boolean isTrustedForwardingProxy(InetAddress address) {
        return address.isLoopbackAddress()
                || trustedProxyLiterals.contains(canonical(address))
                || trustedProxyCidrs.stream().anyMatch(range -> range.matches(address));
    }

    private static String canonical(InetAddress address) {
        return HttpUrlValidation.normalizeHost(address.getHostAddress());
    }

    private static String parseTrustedProxyLiteral(String value) {
        InetAddress parsedAddress = parseNumericAddress(HttpUrlValidation.normalizeHost(value));
        if (parsedAddress == null) {
            throw new IllegalArgumentException("Trusted proxy entry contains an invalid address: " + value);
        }
        return canonical(parsedAddress);
    }

    private static InetAddress parseNumericAddress(String value) {
        String normalized = HttpUrlValidation.normalizeHost(value);
        try {
            String[] ipv4Parts = normalized.split("\\.", -1);
            if (ipv4Parts.length == 4) {
                byte[] address = new byte[4];
                for (int index = 0; index < ipv4Parts.length; index++) {
                    if (ipv4Parts[index].isBlank() || !ipv4Parts[index].chars().allMatch(Character::isDigit)) {
                        return null;
                    }
                    int octet = Integer.parseInt(ipv4Parts[index]);
                    if (octet > 255) {
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
