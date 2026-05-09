package com.example.aichinese.dto.learning;

import java.util.List;

public record AdminScheduleResponse(
        List<ScheduleItemResponse> liveClasses) {
}
