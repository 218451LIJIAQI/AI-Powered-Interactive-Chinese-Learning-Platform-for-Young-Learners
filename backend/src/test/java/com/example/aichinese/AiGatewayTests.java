package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;


import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.ai.AiAudioInput;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderClient;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.example.aichinese.service.ai.AiSafetyGuard;
import com.example.aichinese.service.ai.AiTranscriptionClient;
import com.example.aichinese.service.ai.AiUsageLimiter;

/**
 * Unit coverage of the one path every AI request takes ({@link AiGateway}) and of the bounded instructions
 * ({@link AiPromptBuilder}): an unavailable or blocked request never reaches the provider or the quota, every outcome
 * is logged once, a JSON reply must parse and carry what the feature shows, a recording is written down before it is
 * scored, and no name, e-mail or id of the student is ever sent.
 */
class AiGatewayTests {

    private static final String PROVIDER = "openai";
    private static final String MODEL = "gpt-5.6-luna";
    private static final AiAudioInput RECORDING = new AiAudioInput(new byte[] { 1, 2, 3 }, "voice.webm", "audio/webm");

    private final AiAvailabilityService availability = mock(AiAvailabilityService.class);
    private final AiSafetyGuard safetyGuard = mock(AiSafetyGuard.class);
    private final AiUsageLimiter limiter = mock(AiUsageLimiter.class);
    private final AiProviderClient client = mock(AiProviderClient.class);
    private final AiProviderRouter router = mock(AiProviderRouter.class);
    private final AiTranscriptionClient transcription = mock(AiTranscriptionClient.class);
    private final AiLogService aiLogService = mock(AiLogService.class);
    private final AiGateway gateway = new AiGateway(availability, safetyGuard, limiter,
            new AiPromptBuilder(new AiProperties()), router, transcription, aiLogService);
    private final User student = student();

    @BeforeEach
    void availableProviderWithImagesAndSpeech() {
        when(availability.getAvailability()).thenReturn(
                AiAvailabilityResponse.available(PROVIDER, MODEL, "AI provider is configured.", true, true));
        when(router.resolveClient()).thenReturn(client);
    }

    @Test
    void unavailableProviderAnswersWithTheFriendlyMessageAndLogsTheReason() {
        when(availability.getAvailability()).thenReturn(
                AiAvailabilityResponse.unavailable(PROVIDER, MODEL, "AI provider is disabled in the current backend configuration."));

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hello"));

        assertThat(result.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        assertThat(result.userMessage()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        verify(router, never()).resolveClient();
        verify(limiter, never()).checkProviderCallAllowed(any());
        assertThat(loggedEntry().errorSummary()).contains("disabled");
    }

    @Test
    void safetyCheckBlocksUnsafeWordsOrHistoryBeforeTheProviderAndTheQuota() {
        when(safetyGuard.blocks("unsafe history")).thenReturn(true);

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.XIAOMEI_CONVERSATION,
                        "en", "A fine message")
                .withHistory(List.of(new AiProviderPrompt.AiMessage("user", "unsafe history"))));

        assertThat(result.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(result.userMessage()).isEqualTo(AiSafetyGuard.BLOCKED_INPUT_MESSAGE);
        verify(router, never()).resolveClient();
        verify(limiter, never()).checkProviderCallAllowed(any());
        assertThat(loggedEntry().errorSummary()).isEqualTo(AiSafetyGuard.BLOCKED_INPUT_LOG);
    }

    @Test
    void reachedQuotaIsLoggedAndRethrown() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(TOO_MANY_REQUESTS, "Daily AI request limit reached."))
                .when(limiter).checkProviderCallAllowed(any());

        assertThatThrownBy(() -> gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(loggedEntry().status()).isEqualTo(AiRequestStatus.FAILED);
        verify(router, never()).resolveClient();
    }

    @Test
    void jsonRepliesMustParseAndCarryTheFieldsTheFeatureShows() {
        when(client.generateText(any())).thenReturn(AiProviderResult.success("not json", 4));
        AiExecutionResult invalid = gateway.execute(json("{}"));
        assertThat(invalid.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(invalid.json()).isEmpty();

        when(client.generateText(any())).thenReturn(AiProviderResult.success("{\"other\":1}", 4));
        assertThat(gateway.execute(json("{}")).status()).isEqualTo(AiRequestStatus.FAILED);

        when(client.generateText(any())).thenReturn(AiProviderResult.success("{\"reply\":\"你好\"}", 4));
        AiExecutionResult success = gateway.execute(json("{}"));
        assertThat(success.succeeded()).isTrue();
        assertThat(success.json()).containsEntry("reply", "你好");
        assertThat(loggedEntry().status()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(loggedEntry().tokenCount()).isEqualTo(4);
    }

    @Test
    void providerFailuresAndUnsafeRepliesBecomeFailedRequests() {
        when(client.generateText(any())).thenThrow(new IllegalStateException("boom"));
        assertThat(gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi")).userMessage())
                .isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);

        org.mockito.Mockito.reset(client);
        when(client.generateText(any())).thenReturn(AiProviderResult.success("unsafe reply", 2));
        when(safetyGuard.blocks("unsafe reply")).thenReturn(true);
        AiExecutionResult withheld = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi"));
        assertThat(withheld.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(withheld.userMessage()).isEqualTo(AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE);
    }

    @Test
    void recordingIsWrittenDownFirstAndTheTranscriptIsAddedToThePrompt() {
        when(transcription.transcribe(RECORDING)).thenReturn(AiProviderResult.success("写写", 3));
        when(client.generateText(any())).thenReturn(AiProviderResult.success("{\"score\":70,\"headline\":\"Close\"}", 9));

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.json(student, AiFeatureKey.PRACTICE_CHECK, "en",
                "Score \"谢谢\".", json -> json.containsKey("score")).withAudio(RECORDING));

        assertThat(result.succeeded()).isTrue();
        ArgumentCaptor<AiProviderPrompt> sent = ArgumentCaptor.forClass(AiProviderPrompt.class);
        verify(client).generateText(sent.capture());
        assertThat(sent.getValue().userPrompt()).contains("Score \"谢谢\".", "heard in the recording: \"写写\"");
        assertThat(sent.getValue().systemInstruction()).contains("practice score from 0 to 100");
    }

    @Test
    void silentRecordingAndMissingSpeechSupportNeverReachTheTextModel() {
        when(transcription.transcribe(RECORDING)).thenReturn(AiProviderResult.failure(AiTranscriptionClient.NOTHING_HEARD));
        AiExecutionResult silent = gateway.execute(AiGateway.AiCall.json(student, AiFeatureKey.PRACTICE_CHECK, "en",
                "Score it.", json -> true).withAudio(RECORDING));
        assertThat(silent.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(silent.userMessage()).isEqualTo(AiGateway.NOTHING_HEARD_MESSAGE);

        when(availability.getAvailability()).thenReturn(
                AiAvailabilityResponse.available("deepseek", "deepseek-chat", "AI provider is configured.", false, false));
        AiExecutionResult unsupported = gateway.execute(AiGateway.AiCall.json(student, AiFeatureKey.PRACTICE_CHECK, "en",
                "Score it.", json -> true).withAudio(RECORDING));
        assertThat(unsupported.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        verify(client, never()).generateText(any());
    }

    @Test
    void systemInstructionIsBoundedForYoungAdultsAndFollowsInterfaceLanguage() {
        AiPromptBuilder builder = new AiPromptBuilder(new AiProperties());

        AiProviderPrompt english = builder.buildPrompt(AiFeatureKey.CHAT_TUTOR, "en", "Hello", false, List.of(), null);
        AiProviderPrompt malay = builder.buildPrompt(AiFeatureKey.CHAT_TUTOR, "ms", "Hello", false, List.of(), null);
        AiProviderPrompt chinese = builder.buildPrompt(AiFeatureKey.CHAT_TUTOR, "zh-CN", "Hello", true, List.of(), null);
        AiProviderPrompt xiaomei = builder.buildPrompt(AiFeatureKey.XIAOMEI_CONVERSATION, "en", "Hi", true, List.of(), null);

        assertThat(english.systemInstruction())
                .contains("young adult Mandarin beginners (aged 18 to 25) in Malaysia", "HSK 1-2", "politely decline",
                        "never claim to be a teacher", "never give official grades", "clear, simple English")
                .doesNotContainIgnoringCase("child")
                .doesNotContain("Xiaomei");
        assertThat(malay.systemInstruction()).contains("Bahasa Melayu");
        assertThat(chinese.systemInstruction()).contains("Write explanations in simple Simplified Chinese", "valid JSON only");
        assertThat(xiaomei.systemInstruction()).contains("you are Xiaomei (小美)", "one or two short sentences",
                "Never ask for the learner's full name");
    }

    @Test
    void promptBuilderRedactsDirectIdentifiersFromPromptAndHistory() {
        AiProviderPrompt prompt = new AiPromptBuilder(new AiProperties()).buildPrompt(
                AiFeatureKey.XIAOMEI_CONVERSATION,
                "en",
                "My email is amy.tan@example.com and my phone is 012-345 6789, IC 990101-14-5678.",
                false,
                List.of(new AiProviderPrompt.AiMessage("user", "Call me on +60 12-345 6789")),
                null);

        assertThat(prompt.userPrompt())
                .doesNotContain("amy.tan@example.com", "012-345 6789", "990101-14-5678")
                .contains("[EMAIL]", "[PHONE]", "[ID_NUMBER]");
        assertThat(prompt.messageHistory().get(0).content()).isEqualTo("Call me on [PHONE]");
    }

    @Test
    void usageLogNeverCarriesTheStudentsNameOrEmail() {
        when(client.generateText(any())).thenReturn(AiProviderResult.success("你好 (nǐ hǎo)", 5));

        gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "How do I say hello?"));

        ArgumentCaptor<AiProviderPrompt> sent = ArgumentCaptor.forClass(AiProviderPrompt.class);
        verify(client).generateText(sent.capture());
        String everything = sent.getValue().systemInstruction() + sent.getValue().userPrompt();
        assertThat(everything).doesNotContain(student.getEmail(), "Aisyah", "Rahman");
        assertThat(loggedEntry().feature()).isEqualTo(AiFeatureKey.CHAT_TUTOR);
    }

    private AiGateway.AiCall json(String ignored) {
        return AiGateway.AiCall.json(student, AiFeatureKey.XIAOMEI_CONVERSATION, "en", "Reply as Xiaomei.",
                (Map<String, Object> json) -> json.containsKey("reply"));
    }

    /** The last usage-log row written. */
    private AiLogService.UsageEntry loggedEntry() {
        ArgumentCaptor<AiLogService.UsageEntry> entries = ArgumentCaptor.forClass(AiLogService.UsageEntry.class);
        verify(aiLogService, org.mockito.Mockito.atLeastOnce()).logUsage(entries.capture());
        return entries.getValue();
    }

    private static User student() {
        Role role = new Role();
        role.setName(RoleName.STUDENT);
        User user = new User();
        user.setId(42L);
        user.setEmail("student@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("Aisyah Rahman");
        user.setPreferredLanguage("en");
        user.setRoles(Set.of(role));
        return user;
    }
}
