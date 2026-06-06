package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

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
    private static final String DISPLAY_NAME = "Student";
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

    private static EmailService failingEmailService() {
        return (recipient, subject, body) -> {
            throw new IllegalStateException(SMTP_FAILURE_MESSAGE);
        };
    }

    private static User recipient() {
        User recipient = new User();
        recipient.setId(USER_ID);
        recipient.setEmail(EMAIL);
        recipient.setDisplayName(DISPLAY_NAME);
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
