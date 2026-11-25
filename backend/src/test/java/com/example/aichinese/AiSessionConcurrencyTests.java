package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withEarlierRead;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiChatResponse;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiProviderClient;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiProviderResult;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.TestUploadFixtures;

/** Real account/session changes and database writes, with the external provider replaced locally. */
@TestPropertySource(properties = {"app.ai.enable-real-provider=true", "app.ai.api-key=test-key", "app.ai.provider=openai"})
class AiSessionConcurrencyTests extends ApiIntegrationTest {

    @Autowired private AiService ai;
    @Autowired private AiPracticeCheckService checks;
    @Autowired private AccountDeletionService deletion;
    @Autowired private AuthService auth;
    @Autowired private AdminUserService adminUsers;
    @Autowired private AiLogService usage;
    @Autowired private PlatformTransactionManager transactions;

    @MockitoBean private AiProviderClient provider;
    @MockitoSpyBean private AiGateway gateway;
    @MockitoSpyBean private CurrentAccount currentAccount;

    private SecurityUserDetails principal;

    @BeforeEach
    void prepareStudent() throws Exception {
        String email = uniqueEmail("ai-session-race");
        api.registerStudent(email);
        User student = user(email);
        // Bind the real browser session family from login so "signed_out" tests the actual single-browser logout path.
        String sessionId = jdbc.queryForObject(
                "select family_id from refresh_tokens where user_id = ? order by id desc limit 1",
                String.class, student.getId());
        principal = new SecurityUserDetails(student).withSession(true, sessionId);
        when(provider.generateText(any())).thenAnswer(invocation -> answer(invocation.getArgument(0)));
    }

    static Stream<Arguments> delayedCalls() {
        return Stream.of("deleted", "disabled", "signed_out").flatMap(action -> Stream.of(
                Arguments.of(action, "chat", "provider"),
                Arguments.of(action, "photo", "provider"),
                Arguments.of(action, "quiz", "provider"),
                Arguments.of(action, "chat", "after_log"),
                Arguments.of(action, "photo", "after_log")));
    }

    @ParameterizedTest(name = "{0}: {1} paused at {2}")
    @MethodSource("delayedCalls")
    void endedSessionCannotReturnOrPersistADelayedResult(String action, String feature, String boundary) {
        Throwable failure = catchThrowable(() -> withPausedOperation(pause -> {
            if (boundary.equals("provider")) {
                doAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    pause.run();
                    return answer(invocation.getArgument(0));
                }).when(provider).generateText(any());
            } else {
                doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    pause.run();
                    return result;
                }).when(gateway).execute(any());
            }
            request(feature);
        }, () -> endSession(action)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class,
                    response -> assertThat(response.getStatusCode().value()).isEqualTo(401));
            softly.assertThat(count("select count(*) from ai_chat_sessions where user_id = ?")).as("new chat sessions").isZero();
            softly.assertThat(count("select count(*) from ai_practice_checks where student_user_id = ?")).as("new checks").isZero();
            softly.assertThat(count("select count(*) from ai_usage_logs where user_id = ?"))
                    .as("only usage committed before revocation remains").isEqualTo(boundary.equals("after_log") ? 1L : 0L);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "disabled", "signed_out"})
    void revocationBeforeQuotaAdmissionPreventsProviderCallAndQuotaUse(String action) {
        Throwable failure = catchThrowable(() -> withPausedOperation(pause -> {
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                pause.run();
                return result;
            }).when(currentAccount).require(any());
            request("chat");
        }, () -> endSession(action)));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class,
                    response -> assertThat(response.getStatusCode().value()).isEqualTo(401));
            softly.assertThat(mockingDetails(provider).getInvocations()).as("external provider calls").isEmpty();
            softly.assertThat(count("select count(*) from ai_usage_quota_counters where user_id = ?")).as("new quota reservations").isZero();
            softly.assertThat(count("select count(*) from ai_usage_logs where user_id = ?")).isZero();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "disabled", "signed_out"})
    void revokedPrincipalCannotStartAnotherProviderCall(String action) {
        endSession(action);
        Throwable failure = catchThrowable(() -> request("chat"));
        assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class,
                response -> assertThat(response.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(provider);
        assertThat(count("select count(*) from ai_usage_logs where user_id = ?")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "disabled", "signed_out"})
    void usageWriteRefreshesAnEarlierManagedAccountBeforeValidating(String action) {
        User expected = user(principal.getUsername());
        Throwable failure = catchThrowable(() -> withEarlierRead(transactions,
                () -> userRepository.findById(principal.getUserId()).orElseThrow(),
                () -> usage.logUsage(new AiLogService.UsageEntry(expected, principal.getSessionId(),
                        AiFeatureKey.CHAT_TUTOR, "en", AiRequestStatus.SUCCESS,
                        "test-provider", "test-model", 10, "Question", "Answer", null)),
                () -> endSession(action)));
        assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class,
                response -> assertThat(response.getStatusCode().value()).isEqualTo(401));
        assertThat(count("select count(*) from ai_usage_logs where user_id = ?")).isZero();
    }

    /**
     * The chat stream interrupts its virtual worker when it closes, which cuts the provider call short (or comes just
     * after the answer). The request keeps its usage-log row, though database I/O on an interrupted virtual thread
     * closes the connection, and no turn is stored.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aStreamClosedDuringTheProviderCallStillLogsTheRequestAndStoresNoTurn(boolean providerAnswered)
            throws Exception {
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return providerAnswered
                    ? answer(invocation.getArgument(0))
                    : AiProviderResult.failure("Provider request failed or timed out.");
        }).when(provider).generateText(any());
        Object outcome;
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var future = workers.submit(() -> completeChat(new AiChatRequest("How do I say hello?", "en", null)));
            try {
                outcome = future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof AssertionError assertion) {
                    throw assertion;
                }
                outcome = failure.getCause();
            }
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (providerAnswered) {
            // An answer that nobody receives any more is refused before it is stored.
            assertThat(outcome).isInstanceOf(IllegalStateException.class);
        } else {
            assertThat(outcome).isInstanceOfSatisfying(AiChatResponse.class,
                    response -> assertThat(response.status()).isEqualTo(AiRequestStatus.FAILED));
        }
        assertThat(jdbc.queryForList("select status from ai_usage_logs where user_id = ?", String.class,
                principal.getUserId())).containsExactly(providerAnswered ? "SUCCESS" : "FAILED");
        assertThat(count("select count(*) from ai_chat_sessions where user_id = ?")).isZero();
    }

    private void request(String feature) {
        switch (feature) {
            case "chat" -> completeChat(new AiChatRequest("How do I say hello?", "en", null));
            case "photo" -> checks.check(principal, new AiPracticeCheckService.CheckRequest("HANDWRITING",
                    new MockMultipartFile("file", "writing.png", "image/png", TestUploadFixtures.minimalPng()), "你好", null, "en"));
            case "quiz" -> ai.quizGenerator(principal, new AiQuizGeneratorRequest("Greetings", "Beginner", 1, "en"));
            default -> throw new AssertionError(feature);
        }
    }

    private AiChatResponse completeChat(AiChatRequest request) {
        return ai.completeChat(principal, ai.prepareChat(principal, request));
    }

    private void endSession(String action) {
        switch (action) {
            case "deleted" -> deletion.delete(principal, principal.getUsername());
            case "disabled" -> adminUsers.deactivate(principalFor(ADMIN_EMAIL), principal.getUserId(), "Test account no longer available");
            case "signed_out" -> auth.logout(principal);
            default -> throw new AssertionError(action);
        }
    }

    private long count(String query) {
        return jdbc.queryForObject(query, Long.class, principal.getUserId());
    }

    private static AiProviderResult answer(AiProviderPrompt prompt) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return AiProviderResult.success(switch (prompt.featureKey()) {
            case "CHAT_TUTOR" -> "你好 means hello.";
            case "PRACTICE_CHECK" -> "{\"status\":\"READABLE\",\"score\":80,\"headline\":\"Good practice\","
                    + "\"feedback\":\"The left component is wide.\",\"tip\":\"Leave more room for the right component.\"}";
            case "QUIZ_GENERATOR" -> "{\"title\":\"Greetings\",\"questions\":[{\"questionText\":\"Hello?\",\"options\":[\"你好\",\"再见\",\"谢谢\"],\"correctAnswer\":\"你好\"}]}";
            default -> throw new AssertionError(prompt.featureKey());
        }, 10);
    }
}
