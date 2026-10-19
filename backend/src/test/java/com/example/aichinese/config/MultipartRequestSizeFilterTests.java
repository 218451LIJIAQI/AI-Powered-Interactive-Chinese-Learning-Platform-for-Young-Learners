package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class MultipartRequestSizeFilterTests {

    private static final String POST = "POST";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data; boundary=test";
    private static final String OVERSIZED_UPLOAD_REQUEST_ID = "oversized-upload-request";
    private static final String TOO_LARGE_MESSAGE =
            "The file is too large. Please choose a smaller file and try again.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UploadProperties uploadProperties = uploadProperties();
    private final MultipartRequestSizeFilter filter = new MultipartRequestSizeFilter(uploadProperties, objectMapper);

    @Test
    void rejectsVerificationDocumentsAboveVerificationLimit() throws Exception {
        RejectedRequest rejected = rejectMultipartRequest(
                "/api/v1/teachers/verification-documents", overVerificationDocumentLimit());

        assertPayloadTooLargeEnvelope(rejected.response());
        assertThat(rejected.chain().invoked).isFalse();
    }

    @Test
    void allowsRecordedVideoRequestsUpToVideoLimit() throws Exception {
        MockHttpServletRequest request = multipartRequest(
                "/api/v1/teachers/lessons/1/recorded-videos",
                overVerificationDocumentLimit());
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void allowsFileExactlyAtLimitPlusMultipartOverhead() throws Exception {
        MockHttpServletRequest request = multipartRequest(
                "/api/v1/ai/pronunciation-feedback",
                uploadProperties.getAiAudioMaxFileSize().toBytes() + MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES);
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void rejectsAiAudioAboveAudioLimit() throws Exception {
        RejectedRequest rejected = rejectMultipartRequest("/api/v1/ai/pronunciation-feedback", overAiAudioLimit());

        assertPayloadTooLargeEnvelope(rejected.response());
        assertThat(rejected.chain().invoked).isFalse();
    }

    @Test
    void appliesRouteSpecificLimitsWhenApplicationRunsUnderContextPath() throws Exception {
        MockHttpServletRequest request = multipartRequest(
                "/portal/api/v1/teachers/verification-documents",
                overVerificationDocumentLimit());
        request.setContextPath("/portal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(413);
        assertPayloadTooLargeEnvelope(response);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void rejectedRequestIncludesBoundRequestIdInApiEnvelope() throws Exception {
        RequestIdHolder.set(OVERSIZED_UPLOAD_REQUEST_ID);
        try {
            MockHttpServletRequest request = multipartRequest(
                    "/api/v1/teachers/verification-documents",
                    overVerificationDocumentLimit());
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new TrackingFilterChain());

            JsonNode body = objectMapper.readTree(response.getContentAsString());
            assertThat(body.path("requestId").asText()).isEqualTo(OVERSIZED_UPLOAD_REQUEST_ID);
        } finally {
            RequestIdHolder.clear();
        }
    }

    @Test
    void ignoresNonMultipartRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(POST, "/api/v1/ai/chat");
        request.setContent(new byte[128]);
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    private static UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setVerificationDocumentMaxFileSize(DataSize.ofMegabytes(5));
        properties.setAiAudioMaxFileSize(DataSize.ofMegabytes(10));
        properties.setAiImageMaxFileSize(DataSize.ofMegabytes(10));
        properties.setMaxVideoFileSize(DataSize.ofMegabytes(100));
        return properties;
    }

    private RejectedRequest rejectMultipartRequest(String path, long contentLength) {
        MockHttpServletRequest request = multipartRequest(path, contentLength);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(413);
        return new RejectedRequest(response, chain);
    }

    private static MockHttpServletRequest multipartRequest(String path, long contentLength) {
        MockHttpServletRequest request = new FixedContentLengthRequest(POST, path, contentLength);
        request.addHeader(HttpHeaders.CONTENT_TYPE, MULTIPART_FORM_DATA);
        request.addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(contentLength));
        return request;
    }

    private long overVerificationDocumentLimit() {
        return uploadProperties.getVerificationDocumentMaxFileSize().toBytes() + MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES + 1;
    }

    private long overAiAudioLimit() {
        return uploadProperties.getAiAudioMaxFileSize().toBytes() + MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES + 1;
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

    private static final class FixedContentLengthRequest extends MockHttpServletRequest {
        private final long contentLength;

        private FixedContentLengthRequest(String method, String requestUri, long contentLength) {
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
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            invoked = true;
        }
    }
}
