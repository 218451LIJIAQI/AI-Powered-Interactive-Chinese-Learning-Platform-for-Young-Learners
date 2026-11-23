package com.example.aichinese.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.exception.GlobalExceptionHandler;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.service.ai.AiChatStreamRunner;
import com.example.aichinese.service.ai.AiHelperStatusService;
import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pins {@code POST /ai/chat/stream}: synchronous pre-flight errors return JSON before SSE starts, while streaming
 * failures send safe error events (daily quota verbatim, minute quota generic, unexpected failures generic).
 */
class AiControllerChatStreamTests {

    private static final AiService.PreparedChat PREPARED =
            new AiService.PreparedChat(new User(), "en", "Hello", null, java.util.List.of());

    private final AiService aiService = mock(AiService.class);
    private final AiChatStreamRunner streamRunner = new AiChatStreamRunner(aiService);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AiController(aiService, streamRunner, mock(AiHelperStatusService.class),
                    mock(XiaomeiService.class), mock(AiPracticeCheckService.class), mock(AiResponseReportService.class)))
            .setMessageConverters(
                    new StringHttpMessageConverter(StandardCharsets.UTF_8),
                    new MappingJackson2HttpMessageConverter())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    private final LearningApiTestClient api = new LearningApiTestClient(mockMvc, new ObjectMapper());

    @AfterEach
    void shutdownRunner() {
        streamRunner.shutdown();
    }

    @Test
    void dailyQuotaMessageIsSentAsTheStreamError() throws Exception {
        when(aiService.prepareChat(any(), any())).thenReturn(PREPARED);
        when(aiService.completeChat(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Daily AI request limit reached. Please try again tomorrow."));

        assertThat(streamBody())
                .contains("event:error")
                .contains("Daily AI request limit reached. Please try again tomorrow.");
    }

    @Test
    void minuteQuotaUsesTheGenericRateLimitMessage() throws Exception {
        when(aiService.prepareChat(any(), any())).thenReturn(PREPARED);
        when(aiService.completeChat(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "AI request limit reached. Please wait a minute before trying again."));

        assertThat(streamBody())
                .contains("event:error")
                .contains("You are doing that too often. Please wait a moment and try again.");
    }

    @Test
    void preflightNotFoundReturnsJsonBeforeOpeningSseStream() throws Exception {
        when(aiService.prepareChat(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "AI chat session was not found."));

        mockMvc.perform(MockMvcRequestBuilders.post(LearningApiTestClient.AI_CHAT_STREAM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"languagePreference\":\"en\",\"sessionId\":999}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("AI chat session was not found."));
    }

    /**
     * The stream must still be open when the longest allowed provider deadline has passed, with room for the database
     * work around the call: otherwise an answer could be stored and counted while the student is shown an error.
     */
    @Test
    void theStreamOutlastsTheLongestProviderDeadline() {
        long longestProviderWait = TimeUnit.SECONDS.toMillis(AiProperties.MAX_TIMEOUT_SECONDS);
        assertThat(longestProviderWait + TimeUnit.SECONDS.toMillis(5)).isLessThanOrEqualTo(AiChatStreamRunner.CHAT_STREAM_TIMEOUT_MS);
    }

    @Test
    void successfulReplyStreamsDeltasBeforeCompleteWhileUnavailableSkipsDeltas() throws Exception {
        when(aiService.prepareChat(any(), any())).thenReturn(PREPARED);
        when(aiService.completeChat(any(), any())).thenReturn(
                new AiChatResponse(7L, 42L, "你好 (nǐ hǎo) means hello.", AiRequestStatus.SUCCESS));

        assertThat(streamBody())
                .containsSubsequence("event:delta", "你好 (nǐ hǎo)", "event:complete", "\"status\":\"SUCCESS\"");

        when(aiService.completeChat(any(), any())).thenReturn(
                new AiChatResponse(null, null, "AI tutor is currently unavailable.", AiRequestStatus.UNAVAILABLE));

        assertThat(streamBody())
                .doesNotContain("event:delta")
                .contains("event:complete", "AI tutor is currently unavailable.", "\"status\":\"UNAVAILABLE\"");
    }

    @Test
    void otherFailuresStayGeneric() throws Exception {
        when(aiService.prepareChat(any(), any())).thenReturn(PREPARED);
        when(aiService.completeChat(any(), any())).thenThrow(new IllegalStateException("database detail"));

        assertThat(streamBody())
                .contains("event:error")
                .contains("AI chat stream failed. Please try again.")
                .doesNotContain("database detail");
    }

    private String streamBody() throws Exception {
        return api.chatStream(null, Map.of("message", "Hello", "languagePreference", "en"));
    }
}
