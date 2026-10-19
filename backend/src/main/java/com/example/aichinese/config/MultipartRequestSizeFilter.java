package com.example.aichinese.config;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects obviously oversized multipart requests before Spring parses the
 * request body into {@code MultipartFile} instances.
 *
 * <p>The global servlet multipart limit stays high enough for recorded lesson
 * videos, while this route-aware guard keeps verification documents and AI
 * feedback uploads on their smaller caps.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class MultipartRequestSizeFilter extends OncePerRequestFilter {

    private static final String RECORDED_VIDEO_PATH_SEGMENT = "/recorded-videos";
    private static final String VERIFICATION_DOCUMENTS_PATH = "/api/v1/teachers/verification-documents";
    private static final String AI_PRONUNCIATION_PATH = "/api/v1/ai/pronunciation-feedback";
    private static final String AI_WRITING_PATH = "/api/v1/ai/writing-feedback";
    /**
     * Boundaries, part headers and small form fields sent with the file. Without this allowance a file
     * exactly at its documented limit (SDD 5.9) would be rejected because Content-Length covers the whole body.
     */
    static final long MULTIPART_OVERHEAD_BYTES = 64L * 1024;

    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;

    public MultipartRequestSizeFilter(UploadProperties uploadProperties, ObjectMapper objectMapper) {
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long maxRequestBytes = maxRequestBytes(request);
        long contentLength = request.getContentLengthLong();
        if (exceedsLimit(contentLength, maxRequestBytes)) {
            ApiErrorResponseWriter.write(
                    objectMapper,
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    FriendlyErrorMessageResolver.uploadTooLargeMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean exceedsLimit(long contentLength, long maxRequestBytes) {
        return maxRequestBytes > 0 && contentLength > maxRequestBytes + MULTIPART_OVERHEAD_BYTES;
    }

    private long maxRequestBytes(HttpServletRequest request) {
        if (!isMultipart(request)) {
            return -1;
        }
        String path = normalizedApplicationPath(request);
        if (path == null) {
            return uploadProperties.getMaxFileSize().toBytes();
        }
        if (path.startsWith(VERIFICATION_DOCUMENTS_PATH)) {
            return uploadProperties.getVerificationDocumentMaxFileSize().toBytes();
        }
        if (path.startsWith(AI_PRONUNCIATION_PATH)) {
            return uploadProperties.getAiAudioMaxFileSize().toBytes();
        }
        if (path.startsWith(AI_WRITING_PATH)) {
            return uploadProperties.getAiImageMaxFileSize().toBytes();
        }
        if (path.contains(RECORDED_VIDEO_PATH_SEGMENT)) {
            return uploadProperties.getMaxVideoFileSize().toBytes();
        }
        return uploadProperties.getMaxFileSize().toBytes();
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE);
        return contentType != null
                && contentType.strip().toLowerCase(Locale.ROOT).startsWith("multipart/");
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
}
