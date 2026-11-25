package com.example.aichinese.service.ai;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiService;
import com.example.aichinese.util.RequestIdHolder;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

/**
 * Streams a prepared Ask SparkBot chat turn as UTF-8 Server-Sent Events on a dedicated virtual thread, preserving
 * request-ID logging context, arming cancellation callbacks before work starts, and keeping internal errors generic.
 */
@Component
@RequiredArgsConstructor
public class AiChatStreamRunner {

    private static final Logger log = LoggerFactory.getLogger(AiChatStreamRunner.class);

    /**
     * How long the stream stays open. Longer than the provider deadline can be ({@code AiProperties
     * .MAX_TIMEOUT_SECONDS}) plus the database work around it, so a turn is always answered before the stream closes.
     */
    public static final long CHAT_STREAM_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45);
    private static final int STREAM_CHUNK_SIZE = 24;
    private static final int MAX_CONCURRENT_STREAMS = 32;
    private static final int MAX_STREAMS_PER_USER = 3;

    private final AiService aiService;
    private final ExecutorService streamExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-chat-stream-", 0).factory());
    private final Semaphore activeStreams = new Semaphore(MAX_CONCURRENT_STREAMS);
    private final ConcurrentHashMap<Long, Integer> activeStreamsByUser = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    public SseEmitter stream(SecurityUserDetails currentUser, AiService.PreparedChat prepared) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        if (!tryAcquireStreamSlot(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI request limit reached. Please wait a minute before trying again.");
        }
        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaseSlot = () -> {
            if (released.compareAndSet(false, true)) {
                releaseStreamSlot(userId);
            }
        };
        try {
            SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
            String requestId = RequestIdHolder.get();
            FutureTask<Void> task = new FutureTask<>(
                    () -> sendTurn(emitter, currentUser, prepared, requestId, releaseSlot), null);
            emitter.onCompletion(() -> {
                task.cancel(true);
                releaseSlot.run();
            });
            emitter.onError(ignored -> {
                task.cancel(true);
                releaseSlot.run();
            });
            emitter.onTimeout(() -> {
                task.cancel(true);
                completeQuietly(emitter);
                releaseSlot.run();
            });
            streamExecutor.execute(task);
            return emitter;
        } catch (RuntimeException | Error startupFailure) {
            releaseSlot.run();
            throw startupFailure;
        }
    }

    private boolean tryAcquireStreamSlot(Long userId) {
        if (!activeStreams.tryAcquire()) {
            return false;
        }
        if (userId == null) {
            return true;
        }
        boolean[] allowed = { false };
        activeStreamsByUser.compute(userId, (key, current) -> {
            int count = current == null ? 0 : current;
            if (count >= MAX_STREAMS_PER_USER) {
                return current;
            }
            allowed[0] = true;
            return count + 1;
        });
        if (!allowed[0]) {
            activeStreams.release();
        }
        return allowed[0];
    }

    private void releaseStreamSlot(Long userId) {
        if (userId != null) {
            activeStreamsByUser.computeIfPresent(userId, (key, current) -> current <= 1 ? null : current - 1);
        }
        activeStreams.release();
    }

    private void sendTurn(SseEmitter emitter, SecurityUserDetails currentUser, AiService.PreparedChat prepared,
            String requestId, Runnable releaseSlot) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        RequestIdHolder.bind(requestId);
        try {
            AiChatResponse response = aiService.completeChat(currentUser, prepared);
            // Stream deltas only for successful model replies; non-SUCCESS responses carry fixed English server
            // messages that the frontend localizes on receipt of the complete event.
            if (Thread.currentThread().isInterrupted()
                    || (response.status() == AiRequestStatus.SUCCESS
                            && !sendChunkedText(emitter, response.aiResponse()))) {
                completeQuietly(emitter);
                return;
            }
            emitter.send(SseEmitter.event().name("complete").data(response, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception exception) {
            if (exception instanceof IOException || Thread.currentThread().isInterrupted()) {
                log.debug("AI chat stream ended early for user id {} ({}).", userId, exception.getClass().getSimpleName());
                completeQuietly(emitter);
            } else {
                log.warn("AI chat stream failed for user id {} with exception type {}.",
                        userId, exception.getClass().getSimpleName());
                completeWithSafeError(emitter, safeStreamErrorMessage(exception));
            }
        } catch (Error error) {
            log.error("AI chat stream aborted with fatal error for user id {}: {}", userId, error.getClass().getSimpleName());
            completeWithSafeError(emitter, "AI chat stream failed. Please try again.");
            throw error;
        } finally {
            RequestIdHolder.unbind();
            releaseSlot.run();
        }
    }

    private static boolean sendChunkedText(SseEmitter emitter, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return true;
        }
        int remaining = text.codePointCount(0, text.length());
        for (int start = 0; remaining > 0;) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            int chunkSize = Math.min(STREAM_CHUNK_SIZE, remaining);
            int end = text.offsetByCodePoints(start, chunkSize);
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data(text.substring(start, end)));
            start = end;
            remaining -= chunkSize;
        }
        return !Thread.currentThread().isInterrupted();
    }

    /**
     * 4xx errors raised while streaming (a reached AI quota, a session invalidated mid-request) go through
     * {@link FriendlyErrorMessageResolver}; unexpected 5xx or internal exceptions stay generic.
     */
    private static String safeStreamErrorMessage(Exception exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode().is4xxClientError()) {
            return FriendlyErrorMessageResolver.resolve(statusException.getStatusCode(), statusException.getReason());
        }
        return "AI chat stream failed. Please try again.";
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Stream was already completed on timeout or disconnect.
        }
    }

    private static void completeWithSafeError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(message));
        } catch (IOException | IllegalStateException ignored) {
            // Client may have disconnected; the caller already logged a safe failure summary.
        }
        completeQuietly(emitter);
    }
}
