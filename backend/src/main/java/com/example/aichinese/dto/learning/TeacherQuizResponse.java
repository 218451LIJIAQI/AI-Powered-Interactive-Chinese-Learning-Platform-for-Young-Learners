package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

@JsonPropertyOrder({
        "id",
        "lessonId",
        "lessonTitle",
        "title",
        "description",
        "contentStatus",
        "timeLimitMinutes",
        "totalPoints",
        "questions",
        "createdAt",
        "updatedAt"
})
public record TeacherQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        ContentStatus contentStatus,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<TeacherQuizQuestionResponse> questions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String rejectionReason,
        LearningStage learningStage) {

    public TeacherQuizResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentStatus, "contentStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
