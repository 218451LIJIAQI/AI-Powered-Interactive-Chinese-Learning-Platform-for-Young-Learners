package com.example.aichinese.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A loopback HTTP server that stands in for an AI provider in the provider-client tests. Each exchange is handled
 * on its own thread, so a slow handler never blocks the client's timeout; closing the server stops it.
 */
public final class StubHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("stub-http-", 0).factory());

    private StubHttpServer(String path, HttpHandler handler) throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            created.createContext(path, handler);
            created.setExecutor(executor);
            created.start();
        } catch (RuntimeException failure) {
            created.stop(0);
            executor.shutdownNow();
            throw failure;
        }
        server = created;
    }

    /** Starts a server that answers requests to {@code path} with {@code handler}. */
    public static StubHttpServer start(String path, HttpHandler handler) throws IOException {
        return new StubHttpServer(path, handler);
    }

    /** The {@code http://127.0.0.1:port} base URL of the server. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static String requestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void respondJson(HttpExchange exchange, String json) throws IOException {
        respond(exchange, 200, json);
    }

    public static void respond(HttpExchange exchange, int statusCode, String json) throws IOException {
        try (exchange) {
            byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(statusCode, -1);
                return;
            }
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
