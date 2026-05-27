package com.example.aichinese.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.service.StripeWebhookService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bounds the unauthenticated Stripe webhook body before Spring binds it to a
 * {@code String}. Signature verification must receive the exact raw bytes, so
 * an accepted request is replayed from a small bounded buffer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class StripeWebhookPayloadSizeFilter extends OncePerRequestFilter {

    private static final String STRIPE_WEBHOOK_PATH = "/api/v1/payments/webhooks/stripe";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isStripeWebhookRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxBytes = StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES;
        if (request.getContentLengthLong() > maxBytes) {
            rejectOversizedPayload(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            rejectOversizedPayload(response);
            return;
        }

        filterChain.doFilter(new CachedPayloadRequest(request, body), response);
    }

    private static boolean isStripeWebhookRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && STRIPE_WEBHOOK_PATH.equals(normalizedApplicationPath(request));
    }

    private static String normalizedApplicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return null;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            String path = requestUri.substring(contextPath.length());
            return path.isBlank() ? "/" : path;
        }
        return requestUri;
    }

    private static void rejectOversizedPayload(HttpServletResponse response) throws IOException {
        response.sendError(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Stripe webhook payload exceeds the maximum allowed size.");
    }

    private static final class CachedPayloadRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedPayloadRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedServletInputStream(byte[] body) {
            delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Asynchronous webhook body reads are not supported.");
        }
    }
}
