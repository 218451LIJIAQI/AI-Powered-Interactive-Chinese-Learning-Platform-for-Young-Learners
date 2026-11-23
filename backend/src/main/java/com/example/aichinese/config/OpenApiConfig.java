package com.example.aichinese.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.aichinese.dto.ai.AiChatResponse;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String REFRESH_COOKIE = "refreshCookie";
    private static final String LOGIN_ENDPOINT = "POST /api/v1/auth/login";

    @Bean
    public OpenAPI aiChineseLearningOpenApi(SecurityProperties security) {
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia API")
                        .version("v1")
                        .description("REST API for the AI-Powered Interactive Chinese Learning Platform"
                                + " for Young Adults in Malaysia (MandarinSpark). Authenticate via "
                                + LOGIN_ENDPOINT + " and pass the returned token via the"
                                + " Authorize button (use just the raw JWT; the UI prefixes"
                                + " 'Bearer ' for you). Successful responses documented as application/json also support"
                                + " application/yaml and vendor media types ending in +json through the Accept header.")
                        .license(new License()
                                .name("Proprietary - UPM Final Year Project")
                                .url("https://www.upm.edu.my/")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by " + LOGIN_ENDPOINT))
                        .addSecuritySchemes(REFRESH_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(security.refreshTokenCookieName())
                                .description("HttpOnly session cookie sent by the browser when refreshing the access token.")));
    }

    @Bean
    public OpenApiCustomizer publicOperationSecurity() {
        return api -> api.getPaths().forEach((path, operations) -> {
            boolean publicRead = path.equals(SecurityConfig.HEALTH_PATH) || path.equals(SecurityConfig.PUBLIC_API_ROOT)
                    || path.startsWith(SecurityConfig.PUBLIC_API_ROOT + "/");
            if (publicRead && operations.getGet() != null) {
                operations.getGet().setSecurity(List.of());
            }
            if (operations.getPost() != null && SecurityConfig.PUBLIC_POST_ENDPOINTS.contains(path)) {
                operations.getPost().setSecurity(path.equals(RefreshCookieOriginFilter.REFRESH_PATH)
                        ? List.of(new SecurityRequirement().addList(REFRESH_COOKIE)) : List.of());
            }
        });
    }

    /** The complete event carries JSON inside a text stream, so springdoc cannot infer this schema from the handler. */
    @Bean
    public OpenApiCustomizer chatCompletionSchema() {
        return api -> SpringDocAnnotationsUtils.extractSchema(
                api.getComponents(), AiChatResponse.class, null, null, api.getSpecVersion());
    }
}
