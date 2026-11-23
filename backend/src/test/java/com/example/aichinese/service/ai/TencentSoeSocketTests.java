package com.example.aichinese.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

class TencentSoeSocketTests {

    @Test
    void readsTheFinalAggregateAfterSendingRawPcmAndTheEndFrame(@TempDir Path directory) throws Exception {
        Tomcat server = new Tomcat();
        server.setPort(0);
        server.setBaseDir(directory.toString());
        server.getConnector().setProperty("address", "127.0.0.1");
        var context = server.addContext("", directory.toString());
        Tomcat.addServlet(context, "fallback", new HttpServlet() {}).addMapping("/");
        context.addServletContainerInitializer(new WsSci(), Set.of(RecordingEndpoint.class));
        // Resources close in reverse order, so Tomcat is destroyed even if stopping it fails.
        try (AutoCloseable destroy = server::destroy; AutoCloseable stop = server::stop) {
            server.start();
            try (var client = new TencentSoeSocket(new ObjectMapper())) {
                String url = "ws://127.0.0.1:" + server.getConnector().getLocalPort() + "/oral-evaluation";
                byte[] pcm = { 1, 2, 3, 4 };

                var result = client.assess(URI.create(url), pcm, true, 5);

                assertThat(result.path("SuggestedScore").asInt()).isEqualTo(84);
                assertThatThrownBy(() -> client.assess(URI.create(url + "?unauthorized"), pcm, true, 5))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(TencentSoeSocket.ServiceFailure.class)
                        .satisfies(error -> assertThat(((TencentSoeSocket.ServiceFailure) error.getCause()).code())
                                .isEqualTo(4002));

                byte[] multiFramePcm = new byte[4_096];
                assertThatThrownBy(() -> client.assess(URI.create(url + "?mid-stream-error"), multiFramePcm, false, 5))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(TencentSoeSocket.ServiceFailure.class)
                        .satisfies(error -> assertThat(((TencentSoeSocket.ServiceFailure) error.getCause()).code())
                                .isEqualTo(4002));
            }
        }
    }

    @ServerEndpoint("/oral-evaluation")
    public static class RecordingEndpoint {
        private int binaryMessages;
        private byte[] recording;
        private boolean failMidStream;

        @OnOpen
        public void opened(Session session) throws IOException {
            String query = session.getQueryString();
            if ("unauthorized".equals(query)) {
                session.getBasicRemote().sendText("{\"code\":4002}");
            } else {
                failMidStream = "mid-stream-error".equals(query);
                session.getBasicRemote().sendText("{\"code\":0,\"message\":\"success\"}");
            }
        }

        @OnMessage
        public void audio(ByteBuffer pcm, Session session) throws IOException {
            binaryMessages++;
            recording = new byte[pcm.remaining()];
            pcm.get(recording);
            if (failMidStream) {
                session.getBasicRemote().sendText("{\"code\":4002}");
                session.close();
            }
        }

        @OnMessage
        public void text(String message, Session session) throws IOException {
            if (binaryMessages != 1 || !Arrays.equals(recording, new byte[] { 1, 2, 3, 4 })
                    || !message.equals("{\"type\":\"end\"}")) {
                session.getBasicRemote().sendText("{\"code\":4007}");
                return;
            }
            session.getBasicRemote().sendText("{\"code\":0,\"result\":{\"SentenceId\":0,\"SuggestedScore\":42}}");
            session.getBasicRemote().sendText("{\"code\":0,\"result\":{\"SentenceId\":-1,", false);
            session.getBasicRemote().sendText("\"SuggestedScore\":84}}", true);
            session.getBasicRemote().sendText("{\"code\":0,\"final\":1}");
        }
    }
}
