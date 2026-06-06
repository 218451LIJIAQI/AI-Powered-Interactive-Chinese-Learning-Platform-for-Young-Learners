package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.service.LiveLearningMapper;

class LiveLearningMapperTests {

    private static final String TIME_ZONE = "America/New_York";
    private static final String TITLE = "Tone practice";
    private static final LocalDateTime SCHEDULED_START_AT = LocalDateTime.of(2026, 6, 1, 9, 0);
    private static final LocalDateTime SCHEDULED_END_AT = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 1, 9, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 5, 2, 9, 0);
    private static final int EXPECTED_DURATION_MINUTES = 60;
    private static final long LIVE_CLASS_ID = 42L;
    private static final long ENROLLED_COUNT = 0L;
    private static final boolean ENROLLED = false;
    private static final boolean CAN_ENROL = false;
    private static final boolean ACCESS_ALLOWED = true;
    private static final boolean CAN_JOIN = false;
    private static final boolean SHOW_MEETING_LINK = false;

    private final LiveLearningMapper mapper = new LiveLearningMapper();

    @Test
    void mapsLiveClassTimeZoneToResponseContract() {
        LiveClass liveClass = liveClass();

        LiveClassResponse response = responseFor(liveClass);

        assertThat(response.timeZone()).isEqualTo(TIME_ZONE);
        assertThat(response.durationMinutes()).isEqualTo(EXPECTED_DURATION_MINUTES);
    }

    private LiveClassResponse responseFor(LiveClass liveClass) {
        return mapper.toLiveClassResponse(
                liveClass,
                ENROLLED_COUNT,
                ENROLLED,
                CAN_ENROL,
                ACCESS_ALLOWED,
                CAN_JOIN,
                SHOW_MEETING_LINK);
    }

    private static LiveClass liveClass() {
        LiveClass liveClass = new LiveClass();
        liveClass.setId(LIVE_CLASS_ID);
        liveClass.setTitle(TITLE);
        liveClass.setTimeZone(TIME_ZONE);
        liveClass.setScheduledStartAt(SCHEDULED_START_AT);
        liveClass.setScheduledEndAt(SCHEDULED_END_AT);
        liveClass.setCreatedAt(CREATED_AT);
        liveClass.setUpdatedAt(UPDATED_AT);
        return liveClass;
    }
}
