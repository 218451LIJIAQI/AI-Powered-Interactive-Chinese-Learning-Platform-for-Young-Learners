package com.example.aichinese.service.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

/** A single New Age assessment: wait for authorization, send PCM and end, then use the final aggregate. */
@Component
class TencentSoeSocket implements AutoCloseable {

    private static final int FRAME_MILLISECONDS = 40;
    private static final long FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(FRAME_MILLISECONDS);
    private static final int FRAME_BYTES = PracticeAudioDecoder.BYTES_PER_SECOND * FRAME_MILLISECONDS / 1000;
    private static final int MAX_MESSAGE_CHARS = 1_000_000;
    private static final String END_MESSAGE = "{\"type\":\"end\"}";

    private volatile HttpClient client;
    private volatile boolean closed;
    private final ObjectMapper mapper;

    TencentSoeSocket(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private HttpClient httpClient() {
        HttpClient current = client;
        if (current != null && !closed) {
            return current;
        }
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("Tencent SOE socket is closed.");
            }
            if (client == null) {
                client = HttpClient.newHttpClient();
            }
            return client;
        }
    }

    JsonNode assess(URI uri, byte[] pcm, boolean recordingMode, int timeoutSeconds)
            throws InterruptedException, ExecutionException, TimeoutException {
        ReplyListener listener = new ReplyListener();
        long start = System.nanoTime();
        long deadline = start + TimeUnit.SECONDS.toNanos(timeoutSeconds)
                + (recordingMode ? 0 : TimeUnit.MILLISECONDS.toNanos(5_000L
                        + 1_000L * pcm.length / PracticeAudioDecoder.BYTES_PER_SECOND));
        int handshakeSeconds = Math.min(6, timeoutSeconds);
        long handshakeDeadline = start + TimeUnit.SECONDS.toNanos(handshakeSeconds);
        CompletableFuture<WebSocket> connection = httpClient().newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(handshakeSeconds)).buildAsync(uri, listener);
        WebSocket socket = null;
        try {
            socket = await(connection, handshakeDeadline);
            await(listener.ready, handshakeDeadline);
            try {
                if (!recordingMode) {
                    stream(socket, pcm, listener.result, deadline);
                } else if (!listener.result.isDone()) {
                    await(socket.sendBinary(ByteBuffer.wrap(pcm), true), deadline);
                }
                if (!listener.result.isDone()) {
                    await(socket.sendText(END_MESSAGE, true), deadline);
                }
            } catch (ExecutionException sendFailure) {
                return recoverFromSendFailure(listener.result, sendFailure, deadline);
            }
            return await(listener.result, deadline);
        } finally {
            listener.abort();
            if (socket == null) {
                connection.cancel(true);
            } else {
                socket.abort();
            }
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        closed = true;
        HttpClient current = client;
        client = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private static void stream(WebSocket socket, byte[] pcm, CompletableFuture<JsonNode> result, long deadline)
            throws InterruptedException, ExecutionException, TimeoutException {
        long streamStart = System.nanoTime();
        int frameIndex = 0;
        for (int offset = 0; offset < pcm.length && !result.isDone(); offset += FRAME_BYTES) {
            if (System.nanoTime() >= deadline) {
                throw new TimeoutException();
            }
            int length = Math.min(FRAME_BYTES, pcm.length - offset);
            frameIndex++;
            long nextFrame = streamStart + frameIndex * FRAME_NANOS;
            await(socket.sendBinary(ByteBuffer.wrap(pcm, offset, length), true), deadline);
            if (offset + length < pcm.length && !result.isDone()) {
                long now = System.nanoTime();
                long untilDeadline = deadline - now;
                if (untilDeadline <= 0) {
                    throw new TimeoutException();
                }
                long waitNanos = Math.min(nextFrame - now, untilDeadline);
                if (waitNanos > 0) {
                    try {
                        // Wake immediately if the server finishes or sends an error mid-stream.
                        result.get(waitNanos, TimeUnit.NANOSECONDS);
                        return;
                    } catch (TimeoutException pacingDone) {
                        // Expected pacing delay elapsed; send the next audio frame unless deadline expired.
                    }
                }
            }
        }
    }

    private static JsonNode recoverFromSendFailure(
            CompletableFuture<JsonNode> result, ExecutionException sendFailure, long deadline)
            throws InterruptedException, ExecutionException {
        boolean alreadyDone = result.isDone();
        if (!alreadyDone && !(sendFailure.getCause() instanceof IOException)) {
            throw sendFailure;
        }
        try {
            if (alreadyDone) {
                return result.get();
            }
            long now = System.nanoTime();
            return await(result, now + Math.min(deadline - now, TimeUnit.MILLISECONDS.toNanos(500L)));
        } catch (TimeoutException ignored) {
            // No server frame arrived before grace elapsed; propagate the send failure.
        } catch (ExecutionException serverFailure) {
            if (serverFailure.getCause() instanceof ServiceFailure) {
                throw serverFailure;
            }
        }
        throw sendFailure;
    }

    private static <T> T await(CompletableFuture<T> future, long deadline)
            throws InterruptedException, ExecutionException, TimeoutException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new TimeoutException();
        }
        return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    }

    /** A non-zero error code returned by the Tencent SOE protocol frame. */
    static final class ServiceFailure extends Exception {
        private final int code;

        ServiceFailure(int code) {
            this.code = code;
        }

        int code() {
            return code;
        }
    }

    /** A local transport or framing error (oversized payload, invalid JSON, missing aggregate, or early close). */
    static final class ProtocolFailure extends Exception {
        private final String reason;

        ProtocolFailure(String reason) {
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }

    private final class ReplyListener implements WebSocket.Listener {
        private final CompletableFuture<Void> ready = new CompletableFuture<>();
        private final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        private final StringBuilder message = new StringBuilder();
        private final AtomicBoolean aborted = new AtomicBoolean();
        private volatile WebSocket openedSocket;
        private JsonNode aggregate;

        @Override
        public void onOpen(WebSocket socket) {
            openedSocket = socket;
            if (aborted.get()) {
                socket.abort();
                return;
            }
            socket.request(1);
        }

        void abort() {
            aborted.set(true);
            WebSocket socket = openedSocket;
            if (socket != null) {
                socket.abort();
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            if (data.length() > MAX_MESSAGE_CHARS - message.length()) {
                failed(new ProtocolFailure("oversized_message"));
            } else {
                message.append(data);
                if (last) {
                    readMessage();
                    message.setLength(0);
                }
            }
            if (!result.isDone()) {
                socket.request(1);
            }
            return null;
        }

        private void readMessage() {
            try {
                JsonNode frame = mapper.readTree(message.toString());
                JsonNode codeNode = frame == null ? null : frame.get("code");
                if (codeNode == null || !codeNode.isIntegralNumber()) {
                    failed(new ProtocolFailure("missing_code"));
                    return;
                }
                int code = codeNode.asInt();
                if (code != 0) {
                    failed(new ServiceFailure(code));
                    return;
                }
                ready.complete(null);
                boolean finished = frame.path("final").asInt() == 1;
                JsonNode latest = frame.path("result");
                if (latest.isTextual()) {
                    latest = mapper.readTree(latest.textValue());
                }
                if (latest != null && latest.isObject() && !latest.isEmpty()
                        && (finished || latest.path("SentenceId").asInt(-1) == -1)) {
                    aggregate = latest;
                }
                if (finished) {
                    if (aggregate == null) failed(new ProtocolFailure("missing_aggregate"));
                    else result.complete(aggregate);
                }
            } catch (JsonProcessingException exception) {
                failed(new ProtocolFailure("invalid_json"));
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            failed(new ProtocolFailure("closed_early"));
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            failed(error);
        }

        private void failed(Throwable error) {
            ready.completeExceptionally(error);
            result.completeExceptionally(error);
        }
    }
}
