package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;

/** One teacher application as the admin reviews it: the bio, the current documents and its history. */
public record AdminTeacherApplicationResponse(
        Long teacherId,
        Long userId,
        String fullName,
        String email,
        String bio,
        TeacherVerificationState verificationState,
        LocalDateTime submittedAt,
        List<Document> documents,
        List<HistoryEntry> history) {

    public AdminTeacherApplicationResponse {
        documents = DtoNormalization.immutableListOrEmpty(documents);
        history = DtoNormalization.immutableListOrEmpty(history);
    }

    public record Document(
            Long id,
            TeacherVerificationDocumentType documentType,
            String fileName,
            long fileSize,
            LocalDateTime uploadedAt) {
    }

    public enum HistoryEvent {
        ACCOUNT_CREATED,
        APPLICATION_SUBMITTED,
        APPROVED,
        CHANGES_REQUESTED
    }

    public record HistoryEntry(HistoryEvent event, String actorName, LocalDateTime at, String note) {
    }
}
