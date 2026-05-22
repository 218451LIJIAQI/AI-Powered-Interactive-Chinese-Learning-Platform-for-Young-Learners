package com.example.aichinese.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI / Swagger UI metadata.
 *
 * <p>Defines the global title, description, and a {@code bearerAuth} security
 * scheme so that the Swagger UI's "Authorize" button accepts a raw JWT (the
 * token returned by {@code POST /api/v1/auth/login}) and replays it as
 * {@code Authorization: Bearer <token>} on every subsequent request. Without
 * this configuration, the UI would be browsable but every secured endpoint
 * would 401 because the JWT filter would not see a valid token.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI aiChineseLearningOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Chinese Learning Platform API")
                        .version("v1")
                        .description(
                                "REST API for the AI-Powered Web-Based Interactive Chinese Learning"
                                        + " Platform for Young Learners. Authenticate via "
                                        + "POST /api/v1/auth/login and pass the returned token via the"
                                        + " Authorize button (use just the raw JWT; the UI prefixes"
                                        + " 'Bearer ' for you)."))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by POST /api/v1/auth/login")));
    }
}
