package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.service.LiveLearningMapper;

class LiveLearningMapperTests {

    @Test
    void mapsLiveClassTimeZoneToResponseContract() {
        LiveClass liveClass = new LiveClass();
        liveClass.setId(42L);
        liveClass.setTitle("Tone practice");
        liveClass.setTimeZone("America/New_York");
        liveClass.setScheduledStartAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        liveClass.setScheduledEndAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        liveClass.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        liveClass.setUpdatedAt(LocalDateTime.of(2026, 5, 2, 9, 0));

        var response = new LiveLearningMapper().toLiveClassResponse(liveClass, 0, false, false, true, false, false);

        assertThat(response.timeZone()).isEqualTo("America/New_York");
        assertThat(response.durationMinutes()).isEqualTo(60);
    }
}
