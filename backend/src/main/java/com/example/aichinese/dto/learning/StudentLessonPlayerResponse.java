package com.example.aichinese.dto.learning;

import java.util.List;

public record StudentLessonPlayerResponse(
        Long lessonId,
        String title,
        String description,
        String basicContent,
        Integer estimatedDurationMinutes,
        int completionPercent,
        boolean completed,
        boolean locked,
        String learningType,
        List<LiveClassResponse> liveClasses,
        List<RecordedLessonVideoResponse> recordedVideos) {
}
