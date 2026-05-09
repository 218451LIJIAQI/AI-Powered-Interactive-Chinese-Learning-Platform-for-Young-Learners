package com.example.aichinese.dto.learning;

import java.util.List;

public record TeacherScheduleResponse(
        List<ScheduleItemResponse> upcomingTeachingSessions,
        List<ScheduleItemResponse> recordedLessonUpdates) {
}
