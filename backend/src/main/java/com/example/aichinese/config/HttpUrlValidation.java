package com.example.aichinese.config;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.example.aichinese.util.NetworkAddresses;

/** Validates configured web addresses and normalizes origins for credentialed browser requests. */
final class HttpUrlValidation {

    /** Matches decimal, octal, or 0x-hex IPv4-style numeric host forms (e.g. 0177.0.0.1, 0x7f000001). */
    private static final Pattern NUMERIC_HOST_SEGMENT = Pattern.compile("(?:0x[0-9a-f]+|[0-9]+)");

    private HttpUrlValidation() {
    }

    static URI requireAbsoluteHttpUrl(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be configured.");
        }
        URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            // URISyntaxException includes the full input, which may contain credentials.
            throw new IllegalArgumentException(label + " must be a valid URL. " + exception.getReason());
        }
        String scheme = normalizeScheme(uri.getScheme());
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URL.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(label + " must not contain embedded credentials.");
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException(label + " port must be between 1 and 65535.");
        }
        return uri;
    }

    static void requirePublicHttpsUrl(String value, String label, String productionContext) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must be configured " + productionContext + ".");
        }
        URI uri;
        try {
            uri = requireAbsoluteHttpUrl(value, label);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(label + " must be a valid HTTPS URL " + productionContext + ".", exception);
        }
        requirePublicHttpsUrl(uri, label, productionContext);
    }

    static void requirePublicHttpsUrl(URI uri, String label, String productionContext) {
        if (!"https".equals(normalizeScheme(uri.getScheme()))) {
            throw new IllegalStateException(label + " must use HTTPS " + productionContext + ".");
        }
        if (isLocalDestination(NetworkAddresses.normalizeHost(uri.getHost()))) {
            throw new IllegalStateException(label + " must target a non-loopback public host " + productionContext + ".");
        }
    }

    /** An HTTP origin with a lower-case scheme and host and the default port omitted. */
    static String originOf(URI uri) {
        return originOf(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    static String originOf(String scheme, String host, int port) {
        String normalizedScheme = normalizeScheme(scheme);
        String normalizedHost = NetworkAddresses.normalizeHost(host);
        String hostPart = normalizedHost.contains(":") ? "[" + normalizedHost + "]" : normalizedHost;
        boolean defaultPort = ("http".equals(normalizedScheme) && port == 80)
                || ("https".equals(normalizedScheme) && port == 443);
        String portPart = port < 0 || defaultPort ? "" : ":" + port;
        return normalizedScheme + "://" + hostPart + portPart;
    }

    private static String normalizeScheme(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /** Check literals and the reserved localhost domain locally. DNS names are the deployment's responsibility. */
    private static boolean isLocalDestination(String host) {
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (name.equals("localhost") || name.endsWith(".localhost")) {
            return true;
        }
        // NetworkAddresses.parseLiteral rejects zero-padded IPv4 octets, which fall through to isAmbiguousNumericHost.
        InetAddress address = NetworkAddresses.parseLiteral(name);
        if (address == null) {
            // Scoped IPv6 and ambiguous decimal/octal/hex numeric hosts are unsuitable as public destinations.
            return name.contains(":") || isAmbiguousNumericHost(name);
        }
        return isNonPublicAddress(address);
    }

    private static boolean isAmbiguousNumericHost(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length == 0 || labels.length > 4) {
            return false;
        }
        for (String label : labels) {
            if (!NUMERIC_HOST_SEGMENT.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int b0 = Byte.toUnsignedInt(bytes[0]);
            int b1 = Byte.toUnsignedInt(bytes[1]);
            int b2 = Byte.toUnsignedInt(bytes[2]);
            return b0 == 0 // 0.0.0.0/8 "this network"
                    || (b0 == 100 && (b1 & 0xC0) == 64) // 100.64.0.0/10 shared CGNAT (RFC 6598)
                    || (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) // 192.0.0.0/24 IETF, 192.0.2.0/24 TEST-NET-1
                    || (b0 == 198 && (b1 == 18 || b1 == 19)) // 198.18.0.0/15 benchmarking
                    || (b0 == 198 && b1 == 51 && b2 == 100) // 198.51.100.0/24 TEST-NET-2
                    || (b0 == 203 && b1 == 0 && b2 == 113) // 203.0.113.0/24 TEST-NET-3
                    || b0 >= 240; // 240.0.0.0/4 reserved & 255.255.255.255 broadcast
        }
        if (address instanceof Inet6Address) {
            int b0 = Byte.toUnsignedInt(bytes[0]);
            int b1 = Byte.toUnsignedInt(bytes[1]);
            int b2 = Byte.toUnsignedInt(bytes[2]);
            int b3 = Byte.toUnsignedInt(bytes[3]);
            int b4 = Byte.toUnsignedInt(bytes[4]);
            int b5 = Byte.toUnsignedInt(bytes[5]);
            // Only IANA Global Unicast (2000::/3) can be a public IPv6 destination; everything outside it
            // (including ULA fc00::/7, NAT64 64:ff9b::/96, discard 100::/64, IPv4-compatible/translated) is non-public.
            if ((b0 & 0xE0) != 0x20) {
                return true;
            }
            boolean teredo = b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00; // 2001::/32
            boolean benchmarking = b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x02 && b4 == 0x00 && b5 == 0x00; // 2001:2::/48
            boolean orchid = b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && ((b3 & 0xF0) == 0x10 || (b3 & 0xF0) == 0x20); // 2001:10::/28, 2001:20::/28
            boolean documentation = b0 == 0x20 && b1 == 0x01 && b2 == 0x0D && b3 == 0xB8; // 2001:db8::/32
            boolean sixToFour = b0 == 0x20 && b1 == 0x02; // 2002::/16
            return teredo || benchmarking || orchid || documentation || sixToFour;
        }
        return false;
    }
}
