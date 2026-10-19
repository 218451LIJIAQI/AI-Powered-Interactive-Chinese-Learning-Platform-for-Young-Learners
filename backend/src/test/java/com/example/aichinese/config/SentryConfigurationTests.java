package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;

class SentryConfigurationTests {

    private static final String FILTERED = "[Filtered]";
    private static final String EMAIL = "learner@example.com";
    private static final String REQUEST_ID = "req-123";

    private final SentryConfiguration configuration = new SentryConfiguration();

    @Test
    void beforeSendMasksSensitiveRequestFieldsExtrasAndUserIdentity() {
        SentryEvent event = sensitiveEvent();

        SentryEvent sanitized = configuration.sentryBeforeSendCallback().execute(event, null);

        assertRequestSanitized(sanitized.getRequest());
        assertEventMetadataSanitized(sanitized);
        assertMessageSanitized(sanitized.getMessage());
        assertBreadcrumbsSanitized(sanitized.getBreadcrumbs());
        assertExceptionsSanitized(sanitized.getExceptions());
        assertUserSanitized(sanitized.getUser());
    }

    private static SentryEvent sensitiveEvent() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setUrl("https://api.example.test/reset?token=raw-reset-token");
        request.setQueryString("token=raw-reset-token&safe=true");
        request.setCookies("mandarin_spark_refresh=refresh-token-value; theme=light");
        request.setHeaders(linkedMap(
                entry("Authorization", "Bearer access-token-value"),
                entry("X-Request-Id", REQUEST_ID)));
        request.setEnvs(linkedMap(
                entry("SESSION_ID", "raw-session"),
                entry("SAFE_ENV", "safe")));
        request.setOthers(linkedMap(
                entry("clientSecret", "raw-client-secret"),
                entry("route", "reset")));
        request.setData(linkedMap(
                entry("password", "Secret123!"),
                entry("note", "hello"),
                entry("nested", Map.of("email", EMAIL))));
        event.setRequest(request);
        event.setTransaction("/reset-password?token=raw-transaction-token");
        event.setTags(linkedMap(
                entry("token", "raw-tag-token"),
                entry("page", "reset-password")));
        event.setExtras(linkedMap(
                entry("providerApiKey", "sk_live_secret"),
                entry("message", "token: raw-token-value")));

        Message message = new Message();
        message.setMessage("Reset failed for " + EMAIL);
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
        user.setEmail(EMAIL);
        user.setUsername(EMAIL);
        user.setIpAddress("203.0.113.10");
        user.setData(linkedMap(
                entry("refresh_token", "raw-refresh"),
                entry("tier", "student")));
        event.setUser(user);
        return event;
    }

    private static void assertRequestSanitized(Request request) {
        assertThat(request.getCookies()).isEqualTo(FILTERED);
        assertThat(request.getHeaders())
                .containsEntry("Authorization", FILTERED)
                .containsEntry("X-Request-Id", REQUEST_ID);
        assertThat(request.getEnvs())
                .containsEntry("SESSION_ID", FILTERED)
                .containsEntry("SAFE_ENV", "safe");
        assertThat(request.getOthers())
                .containsEntry("clientSecret", FILTERED)
                .containsEntry("route", "reset");
        assertThat(request.getData().toString())
                .contains(FILTERED)
                .doesNotContain("Secret123")
                .doesNotContain(EMAIL);
        assertThat(request.getQueryString()).contains(FILTERED).doesNotContain("raw-reset-token");
    }

    private static void assertEventMetadataSanitized(SentryEvent event) {
        assertThat(event.getTransaction()).contains(FILTERED).doesNotContain("raw-transaction-token");
        assertThat(event.getTags())
                .containsEntry("token", FILTERED)
                .containsEntry("page", "reset-password");
        assertThat(event.getExtras())
                .containsEntry("providerApiKey", FILTERED)
                .containsEntry("message", "token=" + FILTERED);
    }

    private static void assertMessageSanitized(Message message) {
        assertThat(message.getMessage()).isEqualTo("Reset failed for " + FILTERED);
        assertThat(message.getFormatted()).isEqualTo("Authorization: " + FILTERED);
        assertThat(message.getParams()).containsExactly("password=" + FILTERED);
    }

    private static void assertBreadcrumbsSanitized(List<Breadcrumb> breadcrumbs) {
        Breadcrumb breadcrumb = breadcrumbs.getFirst();
        assertThat(breadcrumb.getMessage()).isEqualTo("Clicked reset token=" + FILTERED);
        assertThat(breadcrumb.getData())
                .containsEntry("Authorization", FILTERED)
                .containsEntry("safe", "navigation");
    }

    private static void assertExceptionsSanitized(List<SentryException> exceptions) {
        assertThat(exceptions.getFirst().getValue())
                .contains("password=" + FILTERED)
                .contains("token=" + FILTERED)
                .doesNotContain("exception-secret")
                .doesNotContain("exception-token");
    }

    private static void assertUserSanitized(User user) {
        assertThat(user.getEmail()).isNull();
        assertThat(user.getId()).isNull();
        assertThat(user.getUsername()).isNull();
        assertThat(user.getIpAddress()).isNull();
        assertThat(user.getData())
                .containsEntry("refresh_token", FILTERED)
                .containsEntry("tier", "student");
    }

    @SafeVarargs
    private static <K, V> LinkedHashMap<K, V> linkedMap(Map.Entry<K, V>... entries) {
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
