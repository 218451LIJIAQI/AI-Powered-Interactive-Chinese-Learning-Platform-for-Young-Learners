package com.example.aichinese.dto.learning;

import java.util.List;

public record StudentScheduleResponse(
        List<ScheduleItemResponse> upcomingLiveClasses,
        List<ScheduleItemResponse> learningTasks,
        List<ScheduleItemResponse> continueWatching) {
}
