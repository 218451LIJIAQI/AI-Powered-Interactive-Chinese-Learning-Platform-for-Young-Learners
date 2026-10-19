package com.example.aichinese.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.AiService;

/**
 * Pins the error event of {@code POST /ai/chat/stream}: a reached AI quota reaches the learner with
 * its friendly 429 text (the daily limit verbatim), while any other failure stays generic.
 */
class AiControllerChatStreamTests {

    private static final String CHAT_BODY = "{\"message\":\"Hello\",\"languagePreference\":\"en\"}";

    private final AiService aiService = mock(AiService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AiController(aiService, mock(AiResponseReportService.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @Test
    void dailyQuotaMessageIsSentAsTheStreamError() throws Exception {
        when(aiService.chat(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Daily AI request limit reached. Please try again tomorrow."));

        assertThat(streamBody())
                .contains("event:error")
                .contains("Daily AI request limit reached. Please try again tomorrow.");
    }

    @Test
    void minuteQuotaUsesTheGenericRateLimitMessage() throws Exception {
        when(aiService.chat(any(), any())).thenThrow(new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "AI request limit reached. Please wait a minute before trying again."));

        assertThat(streamBody())
                .contains("event:error")
                .contains("You are doing that too often. Please wait a moment and try again.");
    }

    @Test
    void otherFailuresStayGeneric() throws Exception {
        when(aiService.chat(any(), any())).thenThrow(new IllegalStateException("database detail"));

        assertThat(streamBody())
                .contains("event:error")
                .contains("AI chat stream failed. Please try again.")
                .doesNotContain("database detail");
    }

    private String streamBody() throws Exception {
        MvcResult pending = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(CHAT_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(5_000);
        MvcResult completed = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn();
        return completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
