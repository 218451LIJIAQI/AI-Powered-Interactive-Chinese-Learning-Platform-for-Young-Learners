package com.example.aichinese;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

class ApiDocsIntegrationTests extends ApiIntegrationTest {

    @Autowired
    private SecurityProperties security;

    @Test
    void servesApiDefinitionAndSwaggerUiOutsideProduction() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/ai/chat/stream'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("swagger-ui-bundle.js")));
    }

    @Test
    void describesTheRecordEachEndpointActuallyReadsOrReturns() throws Exception {
        JsonNode document = apiDocument();
        JsonNode paths = document.required("paths");

        JsonNode check = responseData(document, paths.required("/api/v1/ai/practice-checks").required("post"));
        assertTrue(check.required("properties").has("check"));
        assertEquals(objectMapper.valueToTree(AiRequestStatus.values()), check.requiredAt("/properties/status/enum"));

        JsonNode list = paths.required("/api/v1/admin/learning-path/level-check-questions").required("get");
        JsonNode page = responseData(document, list);
        JsonNode row = component(document, page.requiredAt("/properties/items/items"));
        assertTrue(row.required("properties").has("difficulty"));

        JsonNode activity = paths.required("/api/v1/admin/lessons/{lessonId}/activities").required("post");
        JsonNode input = component(document, activity.required("requestBody").required("content")
                .required("application/json").required("schema"));
        assertEquals(20, input.requiredAt("/properties/cards/maxItems").intValue());
    }

    @Test
    void describesStreamFramesSeparatelyFromHttpErrors() throws Exception {
        JsonNode document = apiDocument();
        String path = "/api/v1/ai/chat/stream";
        JsonNode responses = document.required("paths").required(path).requiredAt("/post/responses");
        JsonNode stream = responses.requiredAt("/200/content").required(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertEquals("string", stream.requiredAt("/schema/type").textValue());
        JsonNode completion = document.requiredAt("/components/schemas/AiChatResponse/properties");
        assertTrue(completion.has("aiResponse"));
        assertEquals(objectMapper.valueToTree(AiRequestStatus.values()), completion.requiredAt("/status/enum"));

        JsonNode error = responses.requiredAt("/default/content").required(MediaType.APPLICATION_JSON_VALUE);
        assertTrue(component(document, error.required("schema")).required("properties").has("fieldErrors"));
        mockMvc.perform(post(path).accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void describesCsvDownloadsAndStoredFilesInTheirActualFormats() throws Exception {
        JsonNode paths = apiDocument().required("paths");
        assertEquals("binary", downloadFormat(paths, "/api/v1/admin/audit-logs/export", "text/csv"));
        assertEquals("binary", downloadFormat(paths, "/api/v1/admin/ai/usage-logs/export", "text/csv"));
        for (String path : List.of("/api/v1/admin/support/attachments/{messageId}",
                "/api/v1/students/classes/lessons/{lessonId}/video/{videoId}/stream")) {
            assertEquals("binary", downloadFormat(paths, path, MediaType.ALL_VALUE));
            JsonNode responses = paths.required(path).requiredAt("/get/responses");
            JsonNode partial = responses.requiredAt("/206/content");
            assertTrue(partial.has(MediaType.ALL_VALUE));
            assertTrue(partial.has("multipart/byteranges"));
            assertTrue(responses.requiredAt("/416/headers").has("Content-Range"));
            assertTrue(responses.required("416").path("content").isEmpty());
        }
    }

    @Test
    void distinguishesPublicOperationsFromBearerAndRefreshCookieAuthentication() throws Exception {
        JsonNode document = apiDocument();
        JsonNode paths = document.required("paths");
        assertTrue(paths.required("/api/v1/auth/login").requiredAt("/post/security").isEmpty());
        assertTrue(paths.required("/api/v1/public/classes").requiredAt("/get/security").isEmpty());
        assertTrue(paths.required("/api/v1/health").requiredAt("/get/security").isEmpty());
        assertTrue(document.requiredAt("/security/0").has("bearerAuth"));
        assertFalse(paths.required("/api/v1/auth/me").required("get").has("security"));

        JsonNode refresh = paths.required("/api/v1/auth/refresh").required("post");
        assertEquals(1, refresh.required("security").size());
        assertTrue(refresh.requiredAt("/security/0").has("refreshCookie"));
        JsonNode cookie = document.requiredAt("/components/securitySchemes/refreshCookie");
        assertEquals("cookie", cookie.required("in").textValue());
        assertEquals(security.refreshTokenCookieName(), cookie.required("name").textValue());
    }

    private JsonNode apiDocument() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsByteArray());
    }

    private static JsonNode responseData(JsonNode document, JsonNode operation) {
        JsonNode schema = operation.requiredAt("/responses/200/content")
                .required(MediaType.APPLICATION_JSON_VALUE).required("schema");
        return component(document, component(document, schema).requiredAt("/properties/data"));
    }

    private static String downloadFormat(JsonNode paths, String path, String mediaType) {
        return paths.required(path).requiredAt("/get/responses/200/content").required(mediaType)
                .requiredAt("/schema/format").textValue();
    }

    private static JsonNode component(JsonNode document, JsonNode reference) {
        return document.requiredAt(reference.required("$ref").textValue().substring(1));
    }
}
