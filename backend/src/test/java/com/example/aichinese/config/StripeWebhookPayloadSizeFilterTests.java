package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.aichinese.service.StripeWebhookService;
import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

class StripeWebhookPayloadSizeFilterTests {

    private static final String METHOD_POST = "POST";
    private static final String WEBHOOK_PATH = "/api/v1/payments/webhooks/stripe";
    private static final int MAX_PAYLOAD_BYTES = StripeWebhookService.MAX_WEBHOOK_PAYLOAD_BYTES;
    private static final long OVER_MAX_PAYLOAD_BYTES = MAX_PAYLOAD_BYTES + 1L;
    private static final String OVERSIZED_WEBHOOK_REQUEST_ID = "oversized-webhook-request";
    private static final String TOO_LARGE_MESSAGE =
            "The file is too large. Please choose a smaller file and try again.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StripeWebhookPayloadSizeFilter filter = new StripeWebhookPayloadSizeFilter(objectMapper);

    @Test
    void rejectsWebhookWhenDeclaredContentLengthExceedsBound() throws Exception {
        RejectedRequest rejected = reject(declaredLengthRequest(WEBHOOK_PATH, OVER_MAX_PAYLOAD_BYTES));

        assertPayloadTooLargeEnvelope(rejected.response());
        assertThat(rejected.chain().invoked).isFalse();
    }

    @Test
    void rejectsOversizedWebhookWhenContentLengthIsUnknown() throws Exception {
        MockHttpServletRequest request = unknownLengthRequest(WEBHOOK_PATH);
        request.setContent(new byte[MAX_PAYLOAD_BYTES + 1]);

        RejectedRequest rejected = reject(request);

        assertPayloadTooLargeEnvelope(rejected.response());
        assertThat(rejected.chain().invoked).isFalse();
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
        MockHttpServletRequest request = new MockHttpServletRequest(METHOD_POST, "/api/v1/ai/chat");
        request.setContent(new byte[MAX_PAYLOAD_BYTES + 1]);
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void appliesBoundWhenApplicationRunsUnderContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(METHOD_POST, "/portal" + WEBHOOK_PATH);
        request.setContextPath("/portal");
        request.setContent(new byte[MAX_PAYLOAD_BYTES + 1]);

        RejectedRequest rejected = reject(request);

        assertPayloadTooLargeEnvelope(rejected.response());
        assertThat(rejected.chain().invoked).isFalse();
    }

    @Test
    void rejectedWebhookIncludesBoundRequestIdInApiEnvelope() throws Exception {
        RequestIdHolder.set(OVERSIZED_WEBHOOK_REQUEST_ID);
        try {
            RejectedRequest rejected = reject(declaredLengthRequest(WEBHOOK_PATH, OVER_MAX_PAYLOAD_BYTES));

            JsonNode body = objectMapper.readTree(rejected.response().getContentAsString());
            assertThat(body.path("requestId").asText()).isEqualTo(OVERSIZED_WEBHOOK_REQUEST_ID);
        } finally {
            RequestIdHolder.clear();
        }
    }

    private static MockHttpServletRequest webhookRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(METHOD_POST, WEBHOOK_PATH);
        request.setContent(body);
        return request;
    }

    private RejectedRequest reject(MockHttpServletRequest request) throws IOException, ServletException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        return new RejectedRequest(response, chain);
    }

    private static MockHttpServletRequest declaredLengthRequest(String path, long contentLength) {
        return new DeclaredLengthRequest(METHOD_POST, path, contentLength);
    }

    private static MockHttpServletRequest unknownLengthRequest(String path) {
        return new UnknownLengthRequest(METHOD_POST, path);
    }

    private void assertPayloadTooLargeEnvelope(MockHttpServletResponse response) throws Exception {
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asText()).isEqualTo(TOO_LARGE_MESSAGE);
        assertThat(body.path("errors").get(0).asText()).isEqualTo(body.path("message").asText());
    }

    private record RejectedRequest(MockHttpServletResponse response, TrackingFilterChain chain) {}

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
