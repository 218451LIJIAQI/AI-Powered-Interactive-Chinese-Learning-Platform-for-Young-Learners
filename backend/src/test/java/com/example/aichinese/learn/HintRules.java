package com.example.aichinese.learn;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.MockMvc;

import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The admin's hint rule per level, changed by a test and put back after it: the test classes share one database, and
 * each expects the rules it found.
 */
final class HintRules implements AutoCloseable {

    private static final String PATH = "/api/v1/admin/ai/hints";

    private final MockMvc mockMvc;
    private final LearningApiTestClient api;
    private final String admin;
    private final JsonNode before;

    private HintRules(MockMvc mockMvc, LearningApiTestClient api, String admin, JsonNode before) {
        this.mockMvc = mockMvc;
        this.api = api;
        this.admin = admin;
        this.before = before;
    }

    /** The rules as they are now, which {@link #close} puts back. */
    static HintRules saved(MockMvc mockMvc, LearningApiTestClient api) throws Exception {
        String admin = api.adminToken();
        JsonNode before = api.data(mockMvc.perform(api.authGet(PATH, admin)).andExpect(status().isOk()).andReturn());
        return new HintRules(mockMvc, api, admin, before);
    }

    /** HSK 1, the rule of a new student, with {@code perSet} hints a set (0: every item); HSK 2 and 3 keep their defaults. */
    void setHsk1(int perSet) throws Exception {
        update(Map.of("levels", List.of(
                Map.of("tier", "HSK1", "delaySeconds", 15, "perSet", perSet),
                Map.of("tier", "HSK2", "delaySeconds", 30, "perSet", 3),
                Map.of("tier", "HSK3", "delaySeconds", 45, "perSet", 1))));
    }

    @Override
    public void close() throws Exception {
        update(before);
    }

    private void update(Object body) throws Exception {
        mockMvc.perform(api.jsonPut(PATH, admin, body)).andExpect(status().isOk());
    }
}
