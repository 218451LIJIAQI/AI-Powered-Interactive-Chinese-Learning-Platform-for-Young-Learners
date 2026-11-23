package com.example.aichinese.service.ai;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final AiService aiService;
    private final ExecutorService streamExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ai-chat-stream-", 0).factory());

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    public SseEmitter stream(SecurityUserDetails currentUser, AiService.PreparedChat prepared) {
        SseEmitter emitter = new SseEmitter(CHAT_STREAM_TIMEOUT_MS);
        String requestId = RequestIdHolder.get();
        FutureTask<Void> task = new FutureTask<>(() -> sendTurn(emitter, currentUser, prepared, requestId), null);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onError(ignored -> task.cancel(true));
        emitter.onTimeout(() -> {
            task.cancel(true);
            completeQuietly(emitter);
        });
        streamExecutor.execute(task);
        return emitter;
    }

    private void sendTurn(SseEmitter emitter, SecurityUserDetails currentUser, AiService.PreparedChat prepared,
            String requestId) {
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
