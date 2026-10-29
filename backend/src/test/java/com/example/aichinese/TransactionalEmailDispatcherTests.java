package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.aichinese.entity.User;
import com.example.aichinese.service.EmailService;
import com.example.aichinese.service.TransactionalEmailDispatcher;

@ExtendWith(OutputCaptureExtension.class)
class TransactionalEmailDispatcherTests {

    private static final long USER_ID = 321L;
    private static final String EMAIL = "student@example.com";
    private static final String RAW_SECRET = "raw-secret";
    private static final String BEARER_SECRET = "bearer-secret";
    private static final String RESET_URL = "https://learn.example/reset-password?token=" + RAW_SECRET;
    private static final String SMTP_FAILURE_MESSAGE =
            "SMTP failed reset_token=" + RAW_SECRET + " Authorization: Bearer " + BEARER_SECRET;
    private static final String EXPECTED_LOG_PREFIX =
            "Asynchronous email delivery failed for user id 321: IllegalStateException";

    @Test
    void deliverLogsPasswordResetFailureWithoutLeakingSecretsOrWarnStackTrace(CapturedOutput output) throws Exception {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        EmailService failingEmailService = failingEmailService();
        TransactionalEmailDispatcher dispatcher = new TransactionalEmailDispatcher(eventPublisher, failingEmailService);
        User recipient = recipient();

        dispatcher.enqueuePasswordReset(
                recipient,
                RESET_URL,
                RAW_SECRET);

        Object event = publishedEvent(eventPublisher);
        Method deliverMethod = deliverMethod(event);

        assertThatCode(() -> deliverMethod.invoke(dispatcher, event)).doesNotThrowAnyException();

        assertThat(output)
                .contains(EXPECTED_LOG_PREFIX)
                .contains("reset_token=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .doesNotContain(RAW_SECRET)
                .doesNotContain(BEARER_SECRET)
                .doesNotContain("at com.example.aichinese");
    }

    @Test
    void teacherDecisionEmailsStripUnicodeWhitespaceFromNotesAndReasons() throws Exception {
        assertThat(deliveredBody(dispatcher ->
                dispatcher.enqueueTeacherApproval(recipient(), "\u3000Approved after review\u3000")))
                .contains("Note: Approved after review")
                .doesNotContain("\u3000");
        assertThat(deliveredBody(dispatcher ->
                dispatcher.enqueueTeacherRejection(recipient(), "\u3000Missing certificate\u3000")))
                .contains("Reason: Missing certificate")
                .doesNotContain("\u3000");
    }

    @Test
    void teacherDecisionEmailsOmitBlankOnlyNotesAndReasons() throws Exception {
        assertThat(deliveredBody(dispatcher -> dispatcher.enqueueTeacherApproval(recipient(), "\u3000")))
                .doesNotContain("Note:");
        assertThat(deliveredBody(dispatcher -> dispatcher.enqueueTeacherRejection(recipient(), "\u3000")))
                .doesNotContain("Reason:");
    }

    /** Enqueues one e-mail on a fresh dispatcher, delivers its event and returns the body that was sent. */
    private static String deliveredBody(Consumer<TransactionalEmailDispatcher> enqueue) throws Exception {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        List<String> bodies = new ArrayList<>();
        TransactionalEmailDispatcher dispatcher =
                new TransactionalEmailDispatcher(eventPublisher, (recipient, subject, body) -> bodies.add(body));
        enqueue.accept(dispatcher);
        Object event = publishedEvent(eventPublisher);
        deliverMethod(event).invoke(dispatcher, event);
        return bodies.getFirst();
    }

    private static EmailService failingEmailService() {
        return (recipient, subject, body) -> {
            throw new IllegalStateException(SMTP_FAILURE_MESSAGE);
        };
    }

    private static User recipient() {
        User recipient = new User();
        recipient.setId(USER_ID);
        recipient.setEmail(EMAIL);
        return recipient;
    }

    private static Object publishedEvent(ApplicationEventPublisher eventPublisher) {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private static Method deliverMethod(Object event) throws NoSuchMethodException {
        return TransactionalEmailDispatcher.class.getDeclaredMethod("deliver", event.getClass());
    }
}
