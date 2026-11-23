package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.classes.LessonEditorService;
import com.example.aichinese.support.ApiIntegrationTest;

/** Real database writes with a local provider stub: a retained turn must remain a question/answer pair. */
class AiChatPersistenceTests extends ApiIntegrationTest {

    @Autowired
    private AiChatSessionRepository sessions;

    @Autowired
    private AiChatMessageRepository messages;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CurrentAccount currentAccount;

    private AiChatMessageRepository messageWrites;
    private AiGateway gateway;
    private AiService service;
    private User student;
    private SecurityUserDetails principal;

    @BeforeEach
    void prepareChat() throws Exception {
        String email = uniqueEmail("chat-persistence");
        api.registerStudentAccount(email);
        student = user(email);
        principal = new SecurityUserDetails(student);
        gateway = mock(AiGateway.class);
        messageWrites = mock(AiChatMessageRepository.class, delegatesTo(messages));
        service = new AiService(
                currentAccount, gateway, sessions, messageWrites, mock(LessonEditorService.class), transactionManager);
        when(gateway.execute(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return AiExecutionResult.success("A Mandarin answer", Map.of());
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedAnswerWriteDoesNotLeaveAnOrphanQuestionOrNewSession(boolean existingSession) {
        AiChatSession session = existingSession ? newSession() : null;
        long sessionCount = sessions.count();
        long messageCount = messages.count();
        doAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            if (message.getSenderRole() == AiChatSenderRole.ASSISTANT) {
                throw new IllegalStateException("Simulated answer write failure");
            }
            return messages.save(message);
        }).when(messageWrites).save(any(AiChatMessage.class));

        assertThatThrownBy(() -> service.chat(principal,
                new AiChatRequest("How do I say hello?", "en", session == null ? null : session.getId())))
                .isInstanceOf(IllegalStateException.class).hasMessage("Simulated answer write failure");

        assertThat(messages.count()).as("failed turns retain no question").isEqualTo(messageCount);
        assertThat(sessions.count()).as("failed first turns retain no empty session").isEqualTo(sessionCount);
    }

    /** The stream interrupts its worker when it closes: an answer that arrives then is neither shown nor stored. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anAnswerArrivingAfterTheStreamClosedIsNotStored(boolean existingSession) throws Exception {
        AiChatSession session = existingSession ? newSession() : null;
        long sessionCount = sessions.count();
        long messageCount = messages.count();
        when(gateway.execute(any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return AiExecutionResult.success("A late answer", Map.of());
        });

        try (var worker = Executors.newSingleThreadExecutor()) {
            var turn = worker.submit(() -> service.chat(principal,
                    new AiChatRequest("How do I say hello?", "en", session == null ? null : session.getId())));
            assertThatThrownBy(() -> turn.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
        }

        assertThat(messages.count()).isEqualTo(messageCount);
        assertThat(sessions.count()).isEqualTo(sessionCount);
    }

    @Test
    void simultaneousTurnsKeepEachAnswerNextToItsOwnQuestion() throws Exception {
        AiChatSession session = newSession();
        when(gateway.execute(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            AiGateway.AiCall call = invocation.getArgument(0);
            boolean first = call.userPrompt().contains("first question");
            return AiExecutionResult.success(first ? "first answer" : "second answer", Map.of());
        });
        var firstAnswerId = new AtomicReference<Long>();
        var secondAnswer = withRowLock(jdbc, "users", pause -> {
            doAnswer(invocation -> {
                AiChatMessage message = messages.save(invocation.getArgument(0));
                if (message.getMessageText().equals("first question")) {
                    pause.run();
                }
                return message;
            }).when(messageWrites).save(any(AiChatMessage.class));
            firstAnswerId.set(service.chat(principal, new AiChatRequest("first question", "en", session.getId())).chatMessageId());
        }, () -> service.chat(principal, new AiChatRequest("second question", "en", session.getId())));
        assertQuestionBefore(firstAnswerId.get(), session.getId(), "first question");
        assertQuestionBefore(secondAnswer.chatMessageId(), session.getId(), "second question");
        assertThat(messages.findBySessionIdOrderByCreatedAtDescIdDesc(session.getId(), Pageable.unpaged())).hasSize(4);
    }

    @Test
    void historyLimitKeepsTheLatestTurnsWhenTimestampsAreEqual() {
        AiChatSession session = newSession();
        LocalDateTime sameInstant = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<AiChatMessage> history = IntStream.range(0, 24).mapToObj(index -> {
            AiChatMessage message = new AiChatMessage();
            message.setSession(session);
            message.setSenderRole(index % 2 == 0 ? AiChatSenderRole.USER : AiChatSenderRole.ASSISTANT);
            message.setMessageText("turn " + index);
            message.setCreatedAt(sameInstant);
            return message;
        }).toList();
        messages.saveAll(history);

        service.chat(principal, new AiChatRequest("Next question", "en", session.getId()));

        ArgumentCaptor<AiGateway.AiCall> sent = ArgumentCaptor.forClass(AiGateway.AiCall.class);
        verify(gateway).execute(sent.capture());
        List<String> expected = IntStream.range(4, 24).mapToObj(index -> "turn " + index).toList();
        assertThat(sent.getValue().history()).extracting(turn -> turn.content()).containsExactlyElementsOf(expected);
    }

    private AiChatSession newSession() {
        AiChatSession session = new AiChatSession();
        session.setUser(student);
        session.setFeatureKey(AiFeatureKey.CHAT_TUTOR);
        return sessions.save(session);
    }

    private void assertQuestionBefore(Long answerId, Long sessionId, String expectedQuestion) {
        // This is also how an AI report finds the prompt associated with the selected answer.
        assertThat(messages.findQuestionBefore(sessionId, answerId)).isEqualTo(expectedQuestion);
    }
}
