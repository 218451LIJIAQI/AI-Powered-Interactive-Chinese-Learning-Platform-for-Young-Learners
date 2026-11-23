package com.example.aichinese;

import static com.example.aichinese.service.ai.AiPromptBuilder.buildPrompt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.FileStorageService.PracticeUpload;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderClient;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.service.ai.AiSafetyGuard;
import com.example.aichinese.service.ai.AiUsageLimiter;
import com.example.aichinese.service.ai.TencentPronunciationClient;

/**
 * Unit coverage of the one path every AI request takes ({@link AiGateway}) and of the bounded instructions
 * ({@link AiPromptBuilder}): an unavailable or blocked request never reaches the provider or the quota; provider outcomes
 * and quota rejections are logged once. A JSON reply must carry the fields the feature shows. Recordings use the
 * pronunciation provider with the same quota and privacy boundary.
 */
class AiGatewayTests {

    private static final String PROVIDER = "openai";
    private static final String MODEL = "gpt-5.6-luna";
    private static final PracticeUpload RECORDING = new PracticeUpload(new byte[] { 1, 2, 3 }, "audio/webm");

    private final AiAvailabilityService availability = mock(AiAvailabilityService.class);
    private final AiSafetyGuard safetyGuard = mock(AiSafetyGuard.class);
    private final AiUsageLimiter limiter = mock(AiUsageLimiter.class);
    private final AiProviderClient client = mock(AiProviderClient.class);
    private final TencentPronunciationClient pronunciation = mock(TencentPronunciationClient.class);
    private final AiLogService aiLogService = mock(AiLogService.class);
    private final AiGateway gateway = new AiGateway(availability, safetyGuard, limiter, client, pronunciation, aiLogService);
    private final User student = student();

    @BeforeEach
    void availableProviderWithImagesAndSpeech() {
        when(availability.getAvailability()).thenReturn(
                AiAvailabilityService.Availability.available(PROVIDER, MODEL, true, true));
    }

    @Test
    void unavailableProviderAnswersWithTheFriendlyMessageAndLogsTheReason() {
        when(availability.getAvailability()).thenReturn(AiAvailabilityService.Availability.unavailable(
                PROVIDER, MODEL, "AI provider is disabled in the current backend configuration."));

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hello"));

        assertThat(result.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        assertThat(result.userMessage()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        verify(client, never()).generateText(any());
        verify(limiter, never()).checkProviderCallAllowed(any(), any());
        assertThat(loggedEntry().errorSummary()).contains("disabled");
    }

    @Test
    void safetyCheckBlocksUnsafeWordsOrHistoryBeforeTheProviderAndTheQuota() {
        when(safetyGuard.blocksRequest("unsafe history")).thenReturn(true);

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.XIAOMEI_CONVERSATION,
                        "en", "A fine message")
                .withHistory(List.of(new AiProviderPrompt.AiMessage("user", "unsafe history"))));

        assertThat(result.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(result.userMessage()).isEqualTo(AiSafetyGuard.BLOCKED_INPUT_MESSAGE);
        verify(client, never()).generateText(any());
        verify(limiter, never()).checkProviderCallAllowed(any(), any());
        assertThat(loggedEntry().errorSummary()).isEqualTo(AiSafetyGuard.BLOCKED_INPUT_LOG);
    }

    @Test
    void reachedQuotaIsLoggedAndRethrown() {
        doThrow(new ResponseStatusException(TOO_MANY_REQUESTS, "Daily AI request limit reached."))
                .when(limiter).checkProviderCallAllowed(any(), any());

        assertThatThrownBy(() -> gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(loggedEntry().status()).isEqualTo(AiRequestStatus.FAILED);
        verify(client, never()).generateText(any());
    }

    @Test
    void endedSessionIsRethrownWithoutMisreportingItAsAQuotaFailure() {
        ResponseStatusException ended = new ResponseStatusException(UNAUTHORIZED, "Session ended.");
        doThrow(ended).when(limiter).checkProviderCallAllowed(any(), any());

        assertThatThrownBy(() -> gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi")))
                .isSameAs(ended);
        verifyNoInteractions(client, pronunciation, aiLogService);
    }

    @Test
    void jsonRepliesMustParseAndCarryTheFieldsTheFeatureShows() {
        AiGateway.AiCall call = xiaomeiJsonCall();
        for (String malformed : List.of("not json", "{\"reply\":\"你好\"} []")) {
            when(client.generateText(any())).thenReturn(AiProviderResult.success(malformed, 4));
            AiExecutionResult invalid = gateway.execute(call);
            assertThat(invalid.status()).isEqualTo(AiRequestStatus.FAILED);
            assertThat(invalid.json()).isEmpty();
        }

        when(client.generateText(any())).thenReturn(AiProviderResult.success("{\"other\":1}", 4));
        assertThat(gateway.execute(call).status()).isEqualTo(AiRequestStatus.FAILED);

        when(client.generateText(any())).thenReturn(AiProviderResult.success("{\"reply\":\"你好\"}", 4));
        AiExecutionResult success = gateway.execute(call);
        assertThat(success.succeeded()).isTrue();
        assertThat(success.json()).containsEntry("reply", "你好");
        List<AiLogService.UsageEntry> entries = loggedEntries(4);
        assertThat(entries.subList(0, 3)).allSatisfy(failed -> assertThat(failed.status()).isEqualTo(AiRequestStatus.FAILED));
        AiLogService.UsageEntry entry = entries.getLast();
        assertThat(entry.status()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(entry.tokenCount()).isEqualTo(4);
    }

    @Test
    void providerFailuresAndUnsafeRepliesBecomeFailedRequests() {
        when(safetyGuard.blocksReply("unsafe reply")).thenReturn(true);
        when(client.generateText(any()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(AiProviderResult.success("unsafe reply", 2));
        AiGateway.AiCall call = AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi");

        AiExecutionResult failed = gateway.execute(call);
        assertThat(failed.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.userMessage()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);

        AiExecutionResult withheld = gateway.execute(call);
        assertThat(withheld.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(withheld.userMessage()).isEqualTo(AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE);
        assertThat(loggedEntries(2))
                .extracting(AiLogService.UsageEntry::status)
                .containsExactly(AiRequestStatus.FAILED, AiRequestStatus.FAILED);
    }

    @Test
    void imageCallIsRejectedAsUnavailableWithoutConsumingQuotaWhenModelLacksImageSupport() {
        when(availability.getAvailability()).thenReturn(
                AiAvailabilityService.Availability.available("deepseek", "deepseek-chat", false, true));

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.PRACTICE_CHECK, "en", "Check")
                .withImage(new AiProviderPrompt.AiImageInput(new byte[] { 1, 2, 3 }, "image/png")));

        assertThat(result.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        assertThat(result.userMessage()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        verifyNoInteractions(limiter, client);
        assertThat(loggedEntry().errorSummary()).contains("image input");
    }

    @Test
    void pronunciationSafetyBlockAndProviderExceptionReturnLoggedFailures() {
        when(safetyGuard.blocksRequest("谢谢")).thenReturn(true);
        var blocked = gateway.assessPronunciation(student, null, "en", "谢谢", RECORDING);
        assertThat(blocked.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(blocked.message()).isEqualTo(AiSafetyGuard.BLOCKED_INPUT_MESSAGE);
        verifyNoInteractions(limiter, pronunciation);

        when(safetyGuard.blocksRequest("谢谢")).thenReturn(false);
        when(pronunciation.assess(RECORDING, "谢谢")).thenThrow(new IllegalStateException("socket reset"));
        var failed = gateway.assessPronunciation(student, null, "en", "谢谢", RECORDING);
        assertThat(failed.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(failed.message()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        assertThat(loggedEntries(2))
                .extracting(AiLogService.UsageEntry::errorSummary)
                .containsExactly(AiSafetyGuard.BLOCKED_INPUT_LOG, "Provider request failed or timed out.");
    }

    /** JSON unicode escapes cannot carry an unsafe word past the reply check: the decoded values are screened too. */
    @Test
    void escapedUnsafeWordsInAJsonReplyAreWithheld() {
        String escaped = "{\"reply\":{\"chinese\":\"\\u53bb\\u6b7b\"}}";
        when(client.generateText(any())).thenReturn(AiProviderResult.success(escaped, 3));
        when(safetyGuard.blocksReply(contains("去死"))).thenReturn(true);

        AiExecutionResult withheld = gateway.execute(xiaomeiJsonCall());

        assertThat(withheld.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(withheld.userMessage()).isEqualTo(AiSafetyGuard.BLOCKED_OUTPUT_MESSAGE);
        assertThat(loggedEntry().errorSummary()).isEqualTo(AiSafetyGuard.BLOCKED_OUTPUT_LOG);
    }

    /** JSON leaf strings are screened individually so two adjacent benign fields cannot form a false-positive match. */
    @Test
    void benignAdjacentJsonFieldsDoNotTriggerCrossLeafReplyBlocks() {
        String reply = "{\"reply\":{\"first\":\"红色\",\"second\":\"情况\"}}";
        when(client.generateText(any())).thenReturn(AiProviderResult.success(reply, 3));
        when(safetyGuard.blocksReply(any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value != null && value.replaceAll("\\s+", "").contains("红色情况");
        });

        AiExecutionResult result = gateway.execute(xiaomeiJsonCall());

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void replyOfOnlyControlOrInvisibleCharactersIsAFailedRequest() {
        String nothingReadable = new String(new char[] { 0, 0x200B }) + "<|im_end|>";
        when(client.generateText(any())).thenReturn(AiProviderResult.success(nothingReadable, 2));

        AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi"));

        assertThat(result.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(result.userMessage()).isEqualTo(AiAvailabilityService.FRIENDLY_UNAVAILABLE_MESSAGE);
        AiLogService.UsageEntry entry = loggedEntry();
        assertThat(entry.errorSummary()).isEqualTo("Provider response text was empty.");
        assertThat(entry.tokenCount()).isEqualTo(2);
    }

    /**
     * The chat stream interrupts its worker when it closes, which cuts the provider call short: the request is still
     * logged with the interrupt set aside (database I/O would fail on it), and the caller still sees the interrupt.
     */
    @Test
    void anInterruptedCallerStillLogsTheRequestAndKeepsTheInterrupt() {
        when(client.generateText(any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return AiProviderResult.failure("Provider request failed or timed out.");
        });
        AtomicBoolean interruptedWhileLogging = new AtomicBoolean(true);
        doAnswer(invocation -> {
            interruptedWhileLogging.set(Thread.currentThread().isInterrupted());
            return null;
        }).when(aiLogService).logUsage(any());

        try {
            AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi"));

            assertThat(result.status()).isEqualTo(AiRequestStatus.FAILED);
            assertThat(interruptedWhileLogging).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept for the caller").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * The stream may close just as the provider answers. The reply is still screened with the interrupt set aside (the
     * AI safety check's setting may need a database read, which would fail on it) and logged, and the caller still sees
     * the interrupt.
     */
    @Test
    void aReplyArrivingAsTheStreamClosesIsStillScreenedAndLogged() {
        when(client.generateText(any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return AiProviderResult.success("你好 (nǐ hǎo)", 5);
        });
        AtomicBoolean interruptedWhileScreening = new AtomicBoolean(true);
        when(safetyGuard.blocksReply(any())).thenAnswer(invocation -> {
            interruptedWhileScreening.set(Thread.currentThread().isInterrupted());
            return false;
        });

        try {
            AiExecutionResult result = gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "Hi"));

            assertThat(result.succeeded()).isTrue();
            assertThat(interruptedWhileScreening).isFalse();
            assertThat(loggedEntry().status()).isEqualTo(AiRequestStatus.SUCCESS);
            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept for the caller").isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void recordingUsesTheAcousticAssessmentEvenWhenOpenAiIsUnavailable() {
        when(availability.getAvailability()).thenReturn(new AiAvailabilityService.Availability(
                false, PROVIDER, MODEL, "Missing OpenAI key", false, true));
        var feedback = new PronunciationFeedback(82, 88, 90, List.of(), List.of());
        when(pronunciation.assess(RECORDING, "谢谢"))
                .thenReturn(new TencentPronunciationClient.Assessment(78, feedback, null));

        var result = gateway.assessPronunciation(student, "session-42", "en", "谢谢", RECORDING);

        assertThat(result.status()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(result.score()).isEqualTo(78);
        assertThat(result.feedback()).isEqualTo(feedback);
        verify(limiter).checkProviderCallAllowed(student, "session-42");
        verifyNoInteractions(client);
        assertThat(loggedEntry().providerName()).isEqualTo("tencent-soe");
    }

    /** A target the assessment would refuse is refused before the quota, like any request text. */
    @Test
    void aTargetNotInChineseCharactersUsesNoneOfTheDaysRequests() {
        var result = gateway.assessPronunciation(student, null, "en", "hello", RECORDING);

        assertThat(result.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(result.message()).isEqualTo("Enter the Mandarin target in Chinese characters.");
        verify(limiter, never()).checkProviderCallAllowed(any(), any());
        verifyNoInteractions(pronunciation);
        assertThat(loggedEntry().errorSummary()).isEqualTo("Invalid pronunciation target.");
    }

    @Test
    void silentRecordingAndMissingSpeechSupportNeverReachTheTextModel() {
        when(pronunciation.assess(RECORDING, "谢谢"))
                .thenReturn(TencentPronunciationClient.Assessment.failed(TencentPronunciationClient.Failure.NO_SPEECH));
        var silent = gateway.assessPronunciation(student, null, "en", "谢谢", RECORDING);
        assertThat(silent.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(silent.message()).isEqualTo(
                "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone.");

        when(availability.getAvailability()).thenReturn(
                AiAvailabilityService.Availability.available("deepseek", "deepseek-chat", false, false));
        var unsupported = gateway.assessPronunciation(student, null, "en", "谢谢", RECORDING);
        assertThat(unsupported.status()).isEqualTo(AiRequestStatus.UNAVAILABLE);
        verify(client, never()).generateText(any());
        assertThat(loggedEntries(2))
                .extracting(AiLogService.UsageEntry::status)
                .containsExactly(AiRequestStatus.FAILED, AiRequestStatus.UNAVAILABLE);
    }

    @Test
    void systemInstructionIsBoundedForYoungAdultsAndFollowsInterfaceLanguage() {
        AiProviderPrompt english = buildPrompt(AiFeatureKey.CHAT_TUTOR, "en", "Hello", false, List.of(), null);
        AiProviderPrompt malay = buildPrompt(AiFeatureKey.CHAT_TUTOR, "ms", "Hello", false, List.of(), null);
        AiProviderPrompt chinese = buildPrompt(AiFeatureKey.CHAT_TUTOR, "zh-CN", "Hello", true, List.of(), null);
        AiProviderPrompt xiaomei = buildPrompt(AiFeatureKey.XIAOMEI_CONVERSATION, "en", "Hi", true, List.of(), null);

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
        AiProviderPrompt prompt = buildPrompt(
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
    void promptSentToTheProviderNeverCarriesTheStudentsNameOrEmail() {
        when(client.generateText(any())).thenReturn(AiProviderResult.success("你好 (nǐ hǎo)", 5));

        gateway.execute(AiGateway.AiCall.text(student, AiFeatureKey.CHAT_TUTOR, "en", "How do I say hello?"));

        ArgumentCaptor<AiProviderPrompt> sent = ArgumentCaptor.forClass(AiProviderPrompt.class);
        verify(client).generateText(sent.capture());
        AiProviderPrompt prompt = sent.getValue();
        String everything = prompt.systemInstruction() + prompt.userPrompt();
        assertThat(everything).doesNotContain(student.getEmail(), "Aisyah", "Rahman");
        assertThat(loggedEntry().feature()).isEqualTo(AiFeatureKey.CHAT_TUTOR);
    }

    private AiGateway.AiCall xiaomeiJsonCall() {
        return AiGateway.AiCall.json(student, AiFeatureKey.XIAOMEI_CONVERSATION, "en", "Reply as Xiaomei.",
                (Map<String, Object> json) -> json.containsKey("reply"));
    }

    /** The single usage-log row written by one gateway request. */
    private AiLogService.UsageEntry loggedEntry() {
        ArgumentCaptor<AiLogService.UsageEntry> entry = ArgumentCaptor.forClass(AiLogService.UsageEntry.class);
        verify(aiLogService).logUsage(entry.capture());
        return entry.getValue();
    }

    private List<AiLogService.UsageEntry> loggedEntries(int expectedCount) {
        ArgumentCaptor<AiLogService.UsageEntry> entries = ArgumentCaptor.forClass(AiLogService.UsageEntry.class);
        verify(aiLogService, times(expectedCount)).logUsage(entries.capture());
        return entries.getAllValues();
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
