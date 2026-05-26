package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class MultipartRequestSizeFilterTests {

    private final UploadProperties uploadProperties = uploadProperties();
    private final MultipartRequestSizeFilter filter = new MultipartRequestSizeFilter(uploadProperties);

    @Test
    void rejectsVerificationDocumentsAboveVerificationLimit() {
        MockHttpServletRequest request = multipartRequest(
                "/api/v1/teachers/verification-documents",
                uploadProperties.getVerificationDocumentMaxFileSize().toBytes() + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void allowsRecordedVideoRequestsUpToVideoLimit() throws Exception {
        MockHttpServletRequest request = multipartRequest(
                "/api/v1/teachers/lessons/1/recorded-videos",
                uploadProperties.getVerificationDocumentMaxFileSize().toBytes() + 1);
        TrackingFilterChain chain = new TrackingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void rejectsAiAudioAboveAudioLimit() {
        MockHttpServletRequest request = multipartRequest(
                "/api/v1/ai/pronunciation-feedback",
                uploadProperties.getAiAudioMaxFileSize().toBytes() + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void appliesRouteSpecificLimitsWhenApplicationRunsUnderContextPath() {
        MockHttpServletRequest request = multipartRequest(
                "/portal/api/v1/teachers/verification-documents",
                uploadProperties.getVerificationDocumentMaxFileSize().toBytes() + 1);
        request.setContextPath("/portal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TrackingFilterChain chain = new TrackingFilterChain();

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.invoked).isFalse();
    }

    @Test
    void ignoresNonMultipartRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/chat");
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

    private static MockHttpServletRequest multipartRequest(String path, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Content-Type", "multipart/form-data; boundary=test");
        request.addHeader("Content-Length", Long.toString(contentLength));
        request.setContent(new byte[Math.toIntExact(contentLength)]);
        return request;
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
