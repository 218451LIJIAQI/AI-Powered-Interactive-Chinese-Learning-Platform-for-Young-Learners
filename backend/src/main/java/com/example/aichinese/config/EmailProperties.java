package com.example.aichinese.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        boolean enabled,
        String from,
        String passwordResetBaseUrl) {

    private static final String DEFAULT_FROM = "no-reply@example.com";
    private static final String DEFAULT_RESET_URL = "http://localhost:5174/reset-password";

    private static final int MAX_FROM_LENGTH = 256;
    private static final int MAX_URL_LENGTH = 2048;

    public String resolveFrom(String fallbackEmail) {
        if (StringUtils.hasText(from)) {
            return sanitizeHeaderValue(from, "Email from address");
        }
        if (StringUtils.hasText(fallbackEmail)) {
            return sanitizeHeaderValue(fallbackEmail, "SMTP username fallback email");
        }
        return DEFAULT_FROM;
    }

    public String resolvePasswordResetBaseUrl() {
        return resolvePasswordResetBaseUri().toString();
    }

    /** Both reset tokens and admin invitations open the configured password page. Only one can be active in a link. */
    public String passwordPageLink(String parameter, String hexToken) {
        return UriComponentsBuilder.fromUri(resolvePasswordResetBaseUri())
                .replaceQueryParam("token")
                .replaceQueryParam("invite")
                .queryParam(parameter, hexToken)
                .build(true)
                .toUriString();
    }

    /**
     * A page of the web app next to the reset page (the reset URL is the one configured link to the frontend), e.g.
     * {@code confirm-email} for the e-mail change link.
     */
    public String resolveFrontendUrl(String page) {
        URI base = resolvePasswordResetBaseUri();
        String rawPath = base.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            base = UriComponentsBuilder.fromUri(base)
                    .replacePath("/")
                    .build(true)
                    .toUri();
        } else if (rawPath.length() > 1 && rawPath.endsWith("/")) {
            String withoutTrailingSlash = rawPath.replaceAll("/+$", "");
            base = UriComponentsBuilder.fromUri(base)
                    .replacePath(withoutTrailingSlash.isEmpty() ? "/" : withoutTrailingSlash)
                    .build(true)
                    .toUri();
        }
        return base.resolve(page).toString();
    }

    private URI resolvePasswordResetBaseUri() {
        if (!StringUtils.hasText(passwordResetBaseUrl)) {
            return URI.create(DEFAULT_RESET_URL);
        }
        String url = passwordResetBaseUrl.strip();
        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Password reset base URL must be at most " + MAX_URL_LENGTH + " characters.");
        }
        URI uri = HttpUrlValidation.requireAbsoluteHttpUrl(url, "Password reset base URL");
        String rawPath = uri.getRawPath();
        if (rawPath != null && rawPath.contains("//")) {
            return UriComponentsBuilder.fromUri(uri)
                    .replacePath(rawPath.replaceAll("/{2,}", "/"))
                    .build(true)
                    .toUri();
        }
        return uri;
    }

    private static String sanitizeHeaderValue(String value, String label) {
        String trimmed = value.strip();
        if (trimmed.length() > MAX_FROM_LENGTH) {
            throw new IllegalArgumentException(
                    label + " must be at most " + MAX_FROM_LENGTH + " characters.");
        }
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + " must not contain line breaks.");
        }
        for (int i = 0, length = trimmed.length(); i < length; i++) {
            char ch = trimmed.charAt(i);
            if ((ch < 0x20 && ch != '\t') || ch == 0x7F) {
                throw new IllegalArgumentException(label + " must not contain control characters.");
            }
        }
        return trimmed;
    }
}
