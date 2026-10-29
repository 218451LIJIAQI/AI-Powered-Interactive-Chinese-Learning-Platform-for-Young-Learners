package com.example.aichinese.exception;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

/** HTTP 429 with a {@code Retry-After} header, so a client can say exactly how long to wait (sign-in lockout). */
public class RetryLaterException extends ResponseStatusException {

    private final long retryAfterSeconds;

    public RetryLaterException(String reason, long retryAfterSeconds) {
        super(TOO_MANY_REQUESTS, reason);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }
}
