package com.example.aichinese.config;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

/** Server-local timestamps reach the browser as instants, and come back as the same moments. */
class JsonTimeIntegrationTests extends ApiIntegrationTest {

    @Test
    void serverLocalTimestampsCarryTheServerOffset() throws Exception {
        LocalDateTime serverTime = LocalDateTime.of(2026, 11, 14, 13, 5, 30);

        String json = objectMapper.writeValueAsString(serverTime);

        OffsetDateTime written = OffsetDateTime.parse(json.replace("\"", ""));
        assertThat(written.toInstant()).isEqualTo(serverTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void responsesSendTimestampsWithAnOffset() throws Exception {
        String email = uniqueEmail("json-time-student");
        String token = api.registerStudent(email);
        Notification notification = new Notification();
        notification.setUser(user(email));
        notification.setTitle("A title");
        notification.setMessage("A message");
        notificationRepository.save(notification);

        String body = mockMvc.perform(api.authGet("/api/v1/notifications", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode first = objectMapper.readTree(body).at("/data/items/0");
        String createdAt = first.path("createdAt").asText();
        assertThat(createdAt).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?(Z|[+-]\\d\\d:\\d\\d)");
        assertThat(OffsetDateTime.parse(createdAt).toInstant())
                .isEqualTo(notificationRepository.findById(notification.getId()).orElseThrow()
                        .getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    /** A timestamp the server sent comes back with its offset (a practice run's start) and means the same moment. */
    @Test
    void requestTimestampsAreReadWithOrWithoutAnOffset() throws Exception {
        LocalDateTime serverTime = LocalDateTime.of(2026, 11, 14, 13, 5, 30);
        String echoed = objectMapper.writeValueAsString(serverTime);

        assertThat(objectMapper.readValue(echoed, LocalDateTime.class)).isEqualTo(serverTime);
        assertThat(objectMapper.readValue("\"2026-11-14T13:05:30\"", LocalDateTime.class)).isEqualTo(serverTime);
        for (ZoneOffset offset : new ZoneOffset[] { ZoneOffset.UTC, ZoneOffset.ofHours(9), ZoneOffset.ofHours(-5) }) {
            OffsetDateTime shifted = serverTime.atZone(ZoneId.systemDefault()).toOffsetDateTime().withOffsetSameInstant(offset);
            assertThat(objectMapper.readValue('"' + shifted.toString() + '"', LocalDateTime.class))
                    .as("offset " + offset)
                    .isEqualTo(serverTime);
        }
        assertThat(objectMapper.readValue("\"   \"", LocalDateTime.class)).isNull();
        assertThatThrownBy(() -> objectMapper.readValue("\"not-a-timestamp\"", LocalDateTime.class))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("12345", LocalDateTime.class))
                .isInstanceOf(JsonMappingException.class);
    }
}
