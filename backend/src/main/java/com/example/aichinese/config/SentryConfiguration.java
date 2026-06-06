package com.example.aichinese.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.aichinese.util.SensitiveDataRedactor;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;

@Configuration(proxyBeanMethods = false)
public class SentryConfiguration {

    private static final String FILTERED = SensitiveDataRedactor.FILTERED;

    @Bean
    SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> sanitize(event);
    }

    private static SentryEvent sanitize(SentryEvent event) {
        if (event == null) {
            return null;
        }
        Request request = event.getRequest();
        if (request != null) {
            request.setUrl(mask(request.getUrl()));
            request.setQueryString(mask(request.getQueryString()));
            request.setFragment(mask(request.getFragment()));
            request.setApiTarget(mask(request.getApiTarget()));
            request.setHeaders(SensitiveDataRedactor.redactStringMap(request.getHeaders(), FILTERED));
            request.setEnvs(SensitiveDataRedactor.redactStringMap(request.getEnvs(), FILTERED));
            request.setOthers(SensitiveDataRedactor.redactStringMap(request.getOthers(), FILTERED));
            request.setUnknown(sanitizedMap(request.getUnknown()));
            request.setCookies(request.getCookies() == null ? null : FILTERED);
            request.setData(SensitiveDataRedactor.redactObject(request.getData(), FILTERED));
        }
        event.setExtras(sanitizedMap(event.getExtras()));
        event.setTags(SensitiveDataRedactor.redactStringMap(event.getTags(), FILTERED));
        event.setTransaction(mask(event.getTransaction()));
        event.setUnknown(sanitizedMap(event.getUnknown()));
        sanitizeMessage(event.getMessage());
        sanitizeBreadcrumbs(event);
        sanitizeExceptions(event);
        sanitizeUser(event);
        return event;
    }

    private static Map<String, Object> sanitizedMap(Map<String, ?> values) {
        return SensitiveDataRedactor.redactMap(values, FILTERED);
    }

    private static void sanitizeUser(SentryEvent event) {
        User user = event.getUser();
        if (user == null) {
            return;
        }
        User sanitizedUser = new User();
        sanitizedUser.setData(SensitiveDataRedactor.redactStringMap(user.getData(), FILTERED));
        sanitizedUser.setUnknown(sanitizedMap(user.getUnknown()));
        event.setUser(sanitizedUser);
    }

    private static void sanitizeMessage(Message message) {
        if (message == null) {
            return;
        }
        message.setMessage(mask(message.getMessage()));
        message.setFormatted(mask(message.getFormatted()));
        var params = message.getParams();
        if (params != null) {
            message.setParams(params.stream()
                    .map(SentryConfiguration::mask)
                    .toList());
        }
        message.setUnknown(sanitizedMap(message.getUnknown()));
    }

    private static void sanitizeBreadcrumbs(SentryEvent event) {
        var breadcrumbs = event.getBreadcrumbs();
        if (breadcrumbs == null) {
            return;
        }
        for (Breadcrumb breadcrumb : breadcrumbs) {
            if (breadcrumb == null) {
                continue;
            }
            breadcrumb.setCategory(mask(breadcrumb.getCategory()));
            breadcrumb.setMessage(mask(breadcrumb.getMessage()));
            breadcrumb.setType(mask(breadcrumb.getType()));
            Map<String, Object> data = sanitizedMap(breadcrumb.getData());
            if (data != null) {
                data.forEach(breadcrumb::setData);
            }
            breadcrumb.setUnknown(sanitizedMap(breadcrumb.getUnknown()));
        }
    }

    private static void sanitizeExceptions(SentryEvent event) {
        var exceptions = event.getExceptions();
        if (exceptions == null) {
            return;
        }
        for (SentryException exception : exceptions) {
            if (exception == null) {
                continue;
            }
            exception.setValue(mask(exception.getValue()));
            exception.setUnknown(sanitizedMap(exception.getUnknown()));
        }
    }

    private static String mask(String value) {
        return SensitiveDataRedactor.redactText(value, FILTERED);
    }
}
