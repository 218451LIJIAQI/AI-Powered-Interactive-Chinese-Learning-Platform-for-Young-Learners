package com.example.aichinese.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI / Swagger UI metadata.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String API_TITLE = "AI-Powered Chinese Learning Platform API";
    private static final String API_VERSION = "v1";
    private static final String API_DESCRIPTION =
            "REST API for the AI-Powered Web-Based Interactive Chinese Learning"
                    + " Platform for Young Learners. Authenticate via "
                    + "POST /api/v1/auth/login and pass the returned token via the"
                    + " Authorize button (use just the raw JWT; the UI prefixes"
                    + " 'Bearer ' for you).";

    @Bean
    public OpenAPI aiChineseLearningOpenApi() {
        return new OpenAPI()
                .info(buildInfo())
                .addSecurityItem(buildSecurityRequirement())
                .components(buildComponents());
    }

    private static Info buildInfo() {
        return new Info()
                .title(API_TITLE)
                .version(API_VERSION)
                .description(API_DESCRIPTION)
                .license(new License()
                        .name("Proprietary - UPM Final Year Project")
                        .url("https://www.upm.edu.my/"));
    }

    private static SecurityRequirement buildSecurityRequirement() {
        return new SecurityRequirement().addList(SECURITY_SCHEME_NAME);
    }

    private static Components buildComponents() {
        return new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT issued by POST /api/v1/auth/login"));
    }
}
