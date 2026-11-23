package com.example.aichinese.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Host and address parsing shared by HTTP configuration and trusted-proxy handling. Never resolves a host name. */
public final class NetworkAddresses {

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-f]*:[0-9a-f:.]*");

    private NetworkAddresses() {
    }

    /** Lower-case host, without the brackets used for an IPv6 literal in a URL. */
    public static String normalizeHost(String value) {
        if (value == null) {
            return "";
        }
        String host = value.strip().toLowerCase(Locale.ROOT);
        return host.startsWith("[") && host.endsWith("]") && host.length() > 2 && host.indexOf(':') >= 0
                ? host.substring(1, host.length() - 1) : host;
    }

    /** Parses a strict dotted IPv4 or IPv6 literal, returning null for a host name, octal-ambiguous octet, or malformed input. */
    public static InetAddress parseLiteral(String value) {
        return parseLiteral(value, false);
    }

    /**
     * Parses an IPv4 or IPv6 literal for rate-limit canonicalization, allowing zero-padded decimal IPv4 octets
     * (e.g. {@code 203.000.113.009} -> {@code 203.0.113.9}) so equivalent spellings share one bucket.
     */
    public static InetAddress parseCanonicalLiteral(String value) {
        return parseLiteral(value, true);
    }

    private static InetAddress parseLiteral(String value, boolean allowZeroPaddedIpv4) {
        String host = normalizeHost(value);
        try {
            if (IPV6_LITERAL.matcher(host).matches()) {
                // Validate any embedded IPv4 tail explicitly so octal-ambiguous forms like ::ffff:012.0.0.1 are rejected.
                if (host.indexOf('.') >= 0) {
                    int lastColon = host.lastIndexOf(':');
                    if (lastColon < 0 || parseIpv4Bytes(host.substring(lastColon + 1), allowZeroPaddedIpv4) == null) {
                        return null;
                    }
                }
                // Bracketed syntax forces JDK InetAddress.getByName onto RFC 2732 IPv6 literal parsing without DNS.
                return InetAddress.getByName("[" + host + "]");
            }
            byte[] ipv4 = parseIpv4Bytes(host, allowZeroPaddedIpv4);
            return ipv4 == null ? null : InetAddress.getByAddress(ipv4);
        } catch (UnknownHostException invalid) {
            return null;
        }
    }

    private static byte[] parseIpv4Bytes(String host, boolean allowZeroPadded) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] address = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3 || !isAsciiDigits(part)
                    || (!allowZeroPadded && part.length() > 1 && part.charAt(0) == '0')) {
                return null;
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                return null;
            }
            address[index] = (byte) octet;
        }
        return address;
    }

    private static boolean isAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }
}
