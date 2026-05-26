package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;

class SentryConfigurationTests {

    private final SentryConfiguration configuration = new SentryConfiguration();

    @Test
    void beforeSendMasksSensitiveRequestFieldsExtrasAndUserIdentity() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setUrl("https://api.example.test/reset?token=raw-reset-token");
        request.setQueryString("token=raw-reset-token&safe=true");
        request.setCookies("mandarin_spark_refresh=refresh-token-value; theme=light");
        request.setHeaders(new LinkedHashMap<>(Map.of(
                "Authorization", "Bearer access-token-value",
                "X-Request-Id", "req-123")));
        request.setEnvs(new LinkedHashMap<>(Map.of(
                "SESSION_ID", "raw-session",
                "SAFE_ENV", "safe")));
        request.setOthers(new LinkedHashMap<>(Map.of(
                "clientSecret", "raw-client-secret",
                "route", "reset")));
        request.setData(new LinkedHashMap<>(Map.of(
                "password", "Secret123!",
                "note", "hello",
                "nested", Map.of("email", "learner@example.com"))));
        event.setRequest(request);
        event.setTransaction("/reset-password?token=raw-transaction-token");
        event.setTags(new LinkedHashMap<>(Map.of(
                "token", "raw-tag-token",
                "page", "reset-password")));
        event.setExtras(new LinkedHashMap<>(Map.of(
                "stripeApiKey", "sk_live_secret",
                "message", "token: raw-token-value")));
        Message message = new Message();
        message.setMessage("Reset failed for learner@example.com");
        message.setFormatted("Authorization: Bearer formatted-token");
        message.setParams(List.of("password=message-secret"));
        event.setMessage(message);
        Breadcrumb breadcrumb = new Breadcrumb("Clicked reset token=breadcrumb-token");
        breadcrumb.setData("Authorization", "Bearer breadcrumb-bearer");
        breadcrumb.setData("safe", "navigation");
        event.setBreadcrumbs(List.of(breadcrumb));
        SentryException exception = new SentryException();
        exception.setValue("Backend rejected password=exception-secret token=exception-token");
        event.setExceptions(List.of(exception));
        User user = new User();
        user.setId("user-123");
        user.setEmail("learner@example.com");
        user.setUsername("learner@example.com");
        user.setIpAddress("203.0.113.10");
        user.setData(new LinkedHashMap<>(Map.of(
                "refresh_token", "raw-refresh",
                "tier", "student")));
        event.setUser(user);

        SentryEvent sanitized = configuration.sentryBeforeSendCallback().execute(event, null);

        assertThat(sanitized.getRequest().getCookies()).isEqualTo("[Filtered]");
        assertThat(sanitized.getRequest().getHeaders())
                .containsEntry("Authorization", "[Filtered]")
                .containsEntry("X-Request-Id", "req-123");
        assertThat(sanitized.getRequest().getEnvs())
                .containsEntry("SESSION_ID", "[Filtered]")
                .containsEntry("SAFE_ENV", "safe");
        assertThat(sanitized.getRequest().getOthers())
                .containsEntry("clientSecret", "[Filtered]")
                .containsEntry("route", "reset");
        assertThat(sanitized.getRequest().getData().toString())
                .contains("[Filtered]")
                .doesNotContain("Secret123")
                .doesNotContain("learner@example.com");
        assertThat(sanitized.getRequest().getQueryString()).contains("[Filtered]").doesNotContain("raw-reset-token");
        assertThat(sanitized.getTransaction()).contains("[Filtered]").doesNotContain("raw-transaction-token");
        assertThat(sanitized.getTags())
                .containsEntry("token", "[Filtered]")
                .containsEntry("page", "reset-password");
        assertThat(sanitized.getExtras())
                .containsEntry("stripeApiKey", "[Filtered]")
                .containsEntry("message", "token=[Filtered]");
        assertThat(sanitized.getMessage().getMessage()).isEqualTo("Reset failed for [Filtered]");
        assertThat(sanitized.getMessage().getFormatted()).isEqualTo("Authorization: [Filtered]");
        assertThat(sanitized.getMessage().getParams()).containsExactly("password=[Filtered]");
        assertThat(sanitized.getBreadcrumbs().getFirst().getMessage()).isEqualTo("Clicked reset token=[Filtered]");
        assertThat(sanitized.getBreadcrumbs().getFirst().getData())
                .containsEntry("Authorization", "[Filtered]")
                .containsEntry("safe", "navigation");
        assertThat(sanitized.getExceptions().getFirst().getValue())
                .contains("password=[Filtered]")
                .contains("token=[Filtered]")
                .doesNotContain("exception-secret")
                .doesNotContain("exception-token");
        assertThat(sanitized.getUser().getEmail()).isNull();
        assertThat(sanitized.getUser().getId()).isNull();
        assertThat(sanitized.getUser().getUsername()).isNull();
        assertThat(sanitized.getUser().getIpAddress()).isNull();
        assertThat(sanitized.getUser().getData())
                .containsEntry("refresh_token", "[Filtered]")
                .containsEntry("tier", "student");
    }
}
