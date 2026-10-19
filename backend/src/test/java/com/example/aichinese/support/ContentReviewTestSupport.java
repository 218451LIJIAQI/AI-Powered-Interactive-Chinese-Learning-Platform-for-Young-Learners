package com.example.aichinese.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives the D4 review workflow from integration tests that need student-visible content:
 * the teacher submits the draft and an admin approves it, exactly as in production.
 */
public final class ContentReviewTestSupport {

    private ContentReviewTestSupport() {
    }

    /** Teacher submits ({@code PACKAGE}, {@code MODULE}, {@code LESSON} or {@code QUIZ}) and an admin approves it. */
    public static void publish(MockMvc mockMvc, String teacherToken, String adminToken, String type, Long id)
            throws Exception {
        submit(mockMvc, teacherToken, type, id);
        approve(mockMvc, adminToken, type, id);
    }

    public static void submit(MockMvc mockMvc, String teacherToken, String type, Long id) throws Exception {
        mockMvc.perform(post(teacherSubmitPath(type, id))
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("SUBMITTED"));
    }

    public static void approve(MockMvc mockMvc, String adminToken, String type, Long id) throws Exception {
        mockMvc.perform(post("/api/v1/admin/content-review/" + type + "/" + id + "/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Looks good\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"));
    }

    public static String teacherSubmitPath(String type, Long id) {
        String segment = switch (type.toUpperCase(Locale.ROOT)) {
            case "PACKAGE" -> "packages";
            case "MODULE" -> "modules";
            case "LESSON" -> "lessons";
            case "QUIZ" -> "quizzes";
            default -> throw new IllegalArgumentException("Unsupported review type for teacher submit: " + type);
        };
        return "/api/v1/teachers/" + segment + "/" + id + "/submit";
    }
}
