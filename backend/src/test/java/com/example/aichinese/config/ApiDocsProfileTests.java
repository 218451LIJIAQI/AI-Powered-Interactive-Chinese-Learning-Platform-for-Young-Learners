package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Pins that Swagger UI and the OpenAPI JSON are switched off under the prod and production
 * profiles (application.yml) and stay available for local development and demonstrations.
 */
class ApiDocsProfileTests {

    private static final List<String> ENABLED = List.of("true", "true");
    private static final List<String> DISABLED = List.of("false", "false");

    @Test
    void apiDocsAreDisabledUnderProductionProfiles() {
        assertThat(apiDocsSettings("prod")).isEqualTo(DISABLED);
        assertThat(apiDocsSettings("production")).isEqualTo(DISABLED);
    }

    @Test
    void apiDocsStayEnabledOutsideProduction() {
        assertThat(apiDocsSettings()).isEqualTo(ENABLED);
        assertThat(apiDocsSettings("demo")).isEqualTo(ENABLED);
    }

    /** Loads only the application configuration files for the given profiles; no beans are created. */
    private static List<String> apiDocsSettings(String... profiles) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .profiles(profiles)
                .run()) {
            Environment environment = context.getEnvironment();
            return List.of(
                    environment.getProperty("springdoc.api-docs.enabled", "true"),
                    environment.getProperty("springdoc.swagger-ui.enabled", "true"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
