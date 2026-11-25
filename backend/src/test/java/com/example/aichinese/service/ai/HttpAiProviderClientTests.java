package com.example.aichinese.service.ai;

import static com.example.aichinese.support.StubHttpServer.respondJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.support.StubHttpServer;

/**
 * The total deadline of one provider call: whatever the HTTP client's own connect and read timeouts allow (none at
 * all here), the call ends at the deadline, so the server never answers after the chat stream or the browser gave up.
 */
class HttpAiProviderClientTests {

    /** No connect or read timeout of its own. */
    private final RestTemplate unboundedClient = new RestTemplate();
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        calls.shutdownNow();
    }

    @Test
    void aCallIsAbandonedAtItsDeadline() throws Exception {
        CountDownLatch releaseServer = new CountDownLatch(1);
        try (StubHttpServer server = blockedResponses(null, releaseServer)) {
            assertThatThrownBy(() -> HttpAiProviderClient.post(calls, unboundedClient, Duration.ofMillis(250),
                    server.baseUrl() + "/responses", "key", Map.of("model", "m")))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasMessageContaining("deadline");
        } finally {
            releaseServer.countDown();
        }
    }

    /** An interrupted caller (the stream closed) stops waiting at once instead of at the deadline. */
    @Test
    void anInterruptedCallerStopsWaitingAtOnce() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        try (StubHttpServer server = blockedResponses(requestReceived, releaseServer)) {
            Thread caller = Thread.currentThread();
            Thread interrupter = Thread.ofVirtual().start(() -> {
                try {
                    if (requestReceived.await(5, TimeUnit.SECONDS)) {
                        caller.interrupt();
                    }
                } catch (InterruptedException ignored) {
                    // Interrupted during cleanup if the call ended early.
                }
            });
            try {
                assertThatThrownBy(() -> HttpAiProviderClient.post(calls, unboundedClient, Duration.ofSeconds(10),
                        server.baseUrl() + "/responses", "key", Map.of("model", "m")))
                        .isInstanceOf(ResourceAccessException.class)
                        .hasMessageContaining("interrupted");
                assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept for the caller").isTrue();
            } finally {
                releaseServer.countDown();
                interrupter.interrupt();
                Thread.interrupted();
                try {
                    interrupter.join();
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void anAnswerWithinTheDeadlineIsReturned() throws Exception {
        try (StubHttpServer server = StubHttpServer.start("/responses", exchange -> respondJson(exchange, "{\"ok\":1}"))) {
            assertThat(HttpAiProviderClient.post(calls, unboundedClient, Duration.ofSeconds(5),
                    server.baseUrl() + "/responses", "key", Map.of("model", "m")))
                    .containsEntry("ok", 1);
        }
    }

    @Test
    void redirectsAreNotFollowedSoBearerCredentialsCannotLeak() throws Exception {
        AtomicBoolean redirectedHit = new AtomicBoolean();
        try (StubHttpServer target = StubHttpServer.start("/leaked", exchange -> {
                    redirectedHit.set(true);
                    respondJson(exchange, "{\"output_text\":\"leaked\"}");
                });
                StubHttpServer server = StubHttpServer.start("/responses", exchange -> {
                    exchange.getResponseHeaders().add("Location", target.baseUrl() + "/leaked");
                    exchange.sendResponseHeaders(307, -1);
                    exchange.close();
                })) {
            AiProperties props = new AiProperties();
            props.setEnableRealProvider(true);
            props.setApiBaseUrl(server.baseUrl());
            props.setApiKey("sk-secret-do-not-forward");
            props.setModelName("gpt-test");
            props.setTimeoutSeconds(5);

            var client = new OpenAiResponsesAiProviderClient(props, new RestTemplateBuilder());
            try {
                AiProviderResult result = client.generateText(
                        new AiProviderPrompt("CHAT_TUTOR", "system", "hello", false, List.of(), null));
                assertThat(result.success()).isFalse();
                assertThat(result.errorSummary()).isEqualTo("Provider answered with HTTP 307.");
                assertThat(redirectedHit).isFalse();
            } finally {
                client.close();
            }
        }
    }

    private static StubHttpServer blockedResponses(CountDownLatch requestReceived, CountDownLatch releaseServer)
            throws IOException {
        return StubHttpServer.start("/responses", exchange -> {
            if (requestReceived != null) {
                requestReceived.countDown();
            }
            try {
                releaseServer.await(10, TimeUnit.SECONDS);
                respondJson(exchange, "{\"late\":true}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client has already given up.
            }
        });
    }
}
