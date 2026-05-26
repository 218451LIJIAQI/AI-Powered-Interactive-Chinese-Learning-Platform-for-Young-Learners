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

    @Test
    void deliverLogsPasswordResetFailureWithoutLeakingSecretsOrWarnStackTrace(CapturedOutput output) throws Exception {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        EmailService failingEmailService = (recipient, subject, body) -> {
            throw new IllegalStateException(
                    "SMTP failed reset_token=raw-secret Authorization: Bearer bearer-secret");
        };
        TransactionalEmailDispatcher dispatcher = new TransactionalEmailDispatcher(eventPublisher, failingEmailService);
        User recipient = new User();
        recipient.setId(321L);
        recipient.setEmail("student@example.com");
        recipient.setDisplayName("Student");

        dispatcher.enqueuePasswordReset(
                recipient,
                "https://learn.example/reset-password?token=raw-secret",
                "raw-secret");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        Object event = eventCaptor.getValue();
        Method deliverMethod = TransactionalEmailDispatcher.class.getDeclaredMethod("deliver", event.getClass());

        assertThatCode(() -> deliverMethod.invoke(dispatcher, event)).doesNotThrowAnyException();

        assertThat(output)
                .contains("Asynchronous email delivery failed for user id 321: IllegalStateException")
                .contains("reset_token=[REDACTED]")
                .contains("Authorization: [REDACTED]")
                .doesNotContain("raw-secret")
                .doesNotContain("bearer-secret")
                .doesNotContain("at com.example.aichinese");
    }
}
