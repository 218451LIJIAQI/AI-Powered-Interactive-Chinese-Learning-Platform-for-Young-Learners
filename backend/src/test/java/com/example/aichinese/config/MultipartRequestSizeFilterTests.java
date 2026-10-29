package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

class MultipartRequestSizeFilterTests {

    private static final String VERIFICATION_DOCUMENTS_PATH = "/api/v1/teachers/verification-documents";
    private static final String LESSON_VIDEO_PATH = "/api/v1/teachers/lessons/1/video";
    private static final String PRACTICE_CHECK_PATH = "/api/v1/ai/practice-checks";
    private static final DataSize VERIFICATION_DOCUMENT_LIMIT = DataSize.ofMegabytes(5);
    private static final DataSize AI_AUDIO_LIMIT = DataSize.ofMegabytes(10);
    private static final DataSize VIDEO_LIMIT = DataSize.ofMegabytes(100);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MultipartRequestSizeFilter filter = new MultipartRequestSizeFilter(uploadProperties(), objectMapper);

    @AfterEach
    void clearRequestId() {
        RequestIdHolder.unbind();
    }

    static Stream<Arguments> uploadsWithinTheirRouteLimit() {
        return Stream.of(
                // Above the 5 MB verification-document cap, then exactly at the 100 MB video cap.
                arguments(LESSON_VIDEO_PATH, withOverhead(VERIFICATION_DOCUMENT_LIMIT) + 1),
                arguments(LESSON_VIDEO_PATH, withOverhead(VIDEO_LIMIT)),
                arguments(PRACTICE_CHECK_PATH, withOverhead(AI_AUDIO_LIMIT)));
    }

    /** A file exactly at its route's limit, plus the multipart overhead, passes. */
    @ParameterizedTest
    @MethodSource("uploadsWithinTheirRouteLimit")
    void allowsUploadsUpToTheirRouteLimit(String path, long contentLength) throws Exception {
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(multipartRequest(path, contentLength), new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    static Stream<Arguments> uploadsAboveTheirRouteLimit() {
        return Stream.of(
                arguments(VERIFICATION_DOCUMENTS_PATH, withOverhead(VERIFICATION_DOCUMENT_LIMIT) + 1),
                arguments(LESSON_VIDEO_PATH, withOverhead(VIDEO_LIMIT) + 1),
                arguments(PRACTICE_CHECK_PATH, withOverhead(AI_AUDIO_LIMIT) + 1),
                // Any other upload route gets the general limit (10 MB), well below the video cap.
                arguments("/api/v1/support/requests", withOverhead(AI_AUDIO_LIMIT) + 1));
    }

    @ParameterizedTest
    @MethodSource("uploadsAboveTheirRouteLimit")
    void rejectsUploadsAboveTheirRouteLimit(String path, long contentLength) throws Exception {
        assertRejectedWithPayloadTooLarge(multipartRequest(path, contentLength));
    }

    @Test
    void appliesRouteSpecificLimitsWhenApplicationRunsUnderContextPath() throws Exception {
        MockHttpServletRequest request = multipartRequest(
                "/portal" + VERIFICATION_DOCUMENTS_PATH, withOverhead(VERIFICATION_DOCUMENT_LIMIT) + 1);
        request.setContextPath("/portal");

        assertRejectedWithPayloadTooLarge(request);
    }

    @Test
    void rejectedRequestIncludesBoundRequestIdInApiEnvelope() throws Exception {
        RequestIdHolder.bind("oversized-upload-request");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(multipartRequest(VERIFICATION_DOCUMENTS_PATH, withOverhead(VERIFICATION_DOCUMENT_LIMIT) + 1),
                response, new TrackingFilterChain());

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("requestId").asText()).isEqualTo("oversized-upload-request");
    }

    @Test
    void ignoresNonMultipartRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat/stream");
        request.setContent(new byte[128]);
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    private static UploadProperties uploadProperties() {
        UploadProperties properties = new UploadProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setVerificationDocumentMaxFileSize(VERIFICATION_DOCUMENT_LIMIT);
        properties.setAiAudioMaxFileSize(AI_AUDIO_LIMIT);
        properties.setAiImageMaxFileSize(DataSize.ofMegabytes(10));
        properties.setMaxVideoFileSize(VIDEO_LIMIT);
        return properties;
    }

    private static long withOverhead(DataSize limit) {
        return limit.toBytes() + MultipartRequestSizeFilter.MULTIPART_OVERHEAD_BYTES;
    }

    private static MockHttpServletRequest multipartRequest(String path, long contentLength) {
        MockHttpServletRequest request = new FixedContentLengthRequest(path, contentLength);
        request.addHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test");
        request.addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(contentLength));
        return request;
    }

    /** The filter answers 413 with the JSON error envelope itself and never passes the request on. */
    private void assertRejectedWithPayloadTooLarge(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("message").asText())
                .isEqualTo("The file is too large. Please choose a smaller file and try again.");
        assertThat(body.path("errors").get(0).asText()).isEqualTo(body.path("message").asText());
        assertThat(chain.invoked).isFalse();
    }

    private static final class FixedContentLengthRequest extends MockHttpServletRequest {
        private final long contentLength;

        private FixedContentLengthRequest(String requestUri, long contentLength) {
            super("POST", requestUri);
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
        public void doFilter(ServletRequest request, ServletResponse response) {
            invoked = true;
        }
    }
}
