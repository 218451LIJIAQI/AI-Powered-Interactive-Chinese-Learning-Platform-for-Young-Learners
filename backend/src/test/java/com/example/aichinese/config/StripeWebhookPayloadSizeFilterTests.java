package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.aichinese.service.StripeWebhookService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

class StripeWebhookPayloadSizeFilterTests {

    private static final String WEBHOOK_PATH = "/api/v1/payments/webhooks/stripe";

    private final StripeWebhookPayloadSizeFilter filter = new StripeWebhookPayloadSizeFilter();

    @Test
    void rejectsWebhookWhenDeclaredContentLengthExceedsBound() throws Exception {
        MockHttpServletRequest request = new DeclaredLengthRequest(
                "POST",
                WEBHOOK_PATH,
                StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES + 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void rejectsOversizedWebhookWhenContentLengthIsUnknown() throws Exception {
        MockHttpServletRequest request = new UnknownLengthRequest("POST", WEBHOOK_PATH);
        request.setContent(new byte[StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void passesBoundedWebhookWithExactRawPayloadPreserved() throws Exception {
        byte[] body = "{\"type\":\"checkout.session.completed\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = webhookRequest(body);
        TrackingFilterChain chain = new TrackingFilterChain(true);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
        assertThat(chain.body).containsExactly(body);
    }

    @Test
    void ignoresOtherJsonEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
        request.setContent(new byte[StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES + 1]);
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void appliesBoundWhenApplicationRunsUnderContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/portal" + WEBHOOK_PATH);
        request.setContextPath("/portal");
        request.setContent(new byte[StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    private static MockHttpServletRequest webhookRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", WEBHOOK_PATH);
        request.setContent(body);
        return request;
    }

    private static final class UnknownLengthRequest extends MockHttpServletRequest {

        private UnknownLengthRequest(String method, String requestUri) {
            super(method, requestUri);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1L;
        }
    }

    private static final class DeclaredLengthRequest extends MockHttpServletRequest {

        private final long contentLength;

        private DeclaredLengthRequest(String method, String requestUri, long contentLength) {
            super(method, requestUri);
            this.contentLength = contentLength;
        }

        @Override
        public int getContentLength() {
            return Math.toIntExact(contentLength);
        }

        @Override
        public long getContentLengthLong() {
            return contentLength;
        }
    }

    private static final class TrackingFilterChain implements FilterChain {

        private final boolean readBody;
        private boolean invoked;
        private byte[] body;

        private TrackingFilterChain(boolean readBody) {
            this.readBody = readBody;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            invoked = true;
            if (readBody) {
                body = request.getInputStream().readAllBytes();
            }
        }
    }
}
