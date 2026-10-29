package com.example.aichinese.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String LOGIN_ENDPOINT = "POST /api/v1/auth/login";

    @Bean
    public OpenAPI aiChineseLearningOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia API")
                        .version("v1")
                        .description("REST API for the AI-Powered Interactive Chinese Learning Platform"
                                + " for Young Adults in Malaysia (MandarinSpark). Authenticate via "
                                + LOGIN_ENDPOINT + " and pass the returned token via the"
                                + " Authorize button (use just the raw JWT; the UI prefixes"
                                + " 'Bearer ' for you).")
                        .license(new License()
                                .name("Proprietary - UPM Final Year Project")
                                .url("https://www.upm.edu.my/")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by " + LOGIN_ENDPOINT)));
    }
}
