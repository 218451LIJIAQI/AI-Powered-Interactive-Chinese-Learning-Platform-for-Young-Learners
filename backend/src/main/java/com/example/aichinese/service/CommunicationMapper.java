package com.example.aichinese.service;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.communication.AuditLogResponse;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.StudentTeacherFeedbackResponse;
import com.example.aichinese.dto.communication.TeacherFeedbackResponse;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;

@Component
public class CommunicationMapper {

    public NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType().name(),
                notification.getNotificationStatus().name(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetPath(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    public TeacherFeedbackResponse toTeacherFeedbackResponse(TeacherFeedback feedback) {
        return new TeacherFeedbackResponse(
                feedback.getId(),
                feedback.getTeacher() != null ? feedback.getTeacher().getId() : null,
                displayName(feedback.getTeacher()),
                feedback.getStudent() != null ? feedback.getStudent().getId() : null,
                displayName(feedback.getStudent()),
                feedback.getLesson() != null ? feedback.getLesson().getId() : null,
                feedback.getLesson() != null ? feedback.getLesson().getTitle() : null,
                feedback.getQuizAttempt() != null ? feedback.getQuizAttempt().getId() : null,
                quizTitle(feedback.getQuizAttempt()),
                feedback.getPracticeSubmission() != null ? feedback.getPracticeSubmission().getId() : null,
                feedback.getFeedbackType(),
                feedback.getFeedbackMessage(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    public StudentTeacherFeedbackResponse toStudentTeacherFeedbackResponse(TeacherFeedback feedback) {
        return new StudentTeacherFeedbackResponse(
                feedback.getId(),
                displayName(feedback.getTeacher()),
                feedback.getLesson() != null ? feedback.getLesson().getId() : null,
                feedback.getLesson() != null ? feedback.getLesson().getTitle() : null,
                feedback.getQuizAttempt() != null ? feedback.getQuizAttempt().getId() : null,
                quizTitle(feedback.getQuizAttempt()),
                feedback.getPracticeSubmission() != null ? feedback.getPracticeSubmission().getId() : null,
                feedback.getFeedbackType(),
                feedback.getFeedbackMessage(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    public ModerationRecordResponse toModerationRecordResponse(ContentModerationRecord record) {
        return new ModerationRecordResponse(
                record.getId(),
                record.getTargetType(),
                record.getTargetId(),
                record.getTargetTitleSnapshot(),
                record.getActionTaken(),
                record.getNote(),
                record.getAdminUser() != null ? record.getAdminUser().getId() : null,
                record.getAdminUser() != null ? record.getAdminUser().getDisplayName() : null,
                record.getRelatedTeacher() != null ? record.getRelatedTeacher().getId() : null,
                displayName(record.getRelatedTeacher()),
                record.getCreatedAt());
    }

    public AuditLogResponse toAuditLogResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUser() != null ? auditLog.getActorUser().getId() : null,
                auditLog.getActorEmail(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDescription(),
                auditLog.getIpAddress(),
                auditLog.getRequestId(),
                auditLog.getCreatedAt());
    }

    private static String displayName(TeacherProfile teacher) {
        return displayName(teacher == null ? null : teacher.getUser());
    }

    private static String displayName(StudentProfile student) {
        return displayName(student == null ? null : student.getUser());
    }

    private static String displayName(User user) {
        return user == null ? null : user.getDisplayName();
    }

    private static String quizTitle(QuizAttempt quizAttempt) {
        return quizAttempt == null || quizAttempt.getQuiz() == null
                ? null
                : quizAttempt.getQuiz().getTitle();
    }
}
