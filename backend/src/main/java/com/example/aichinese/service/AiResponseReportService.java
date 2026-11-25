package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiPracticeCheck;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.EnumLookups;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.PersonalDataRedactor;

import lombok.RequiredArgsConstructor;

/**
 * Reports of AI answers: a student reports an Ask SparkBot answer, a Xiaomei reply, a quiz, a story, a practice score
 * or a learning-plan note, and an admin resolves it (the answer was wrong and the admin acted) or dismisses it (the
 * answer was fine) with a note the student reads in My reports. Each report keeps its own copy of what was asked and
 * answered.
 */
@Service
@RequiredArgsConstructor
public class AiResponseReportService {

    public static final String TARGET_TYPE = "AI_RESPONSE_REPORT";
    /** The audit actions of an admin's decision on a report. */
    public static final String REVIEW_RESOLVED_ACTION = "AI_RESPONSE_REPORT_RESOLVED";
    public static final String REVIEW_DISMISSED_ACTION = "AI_RESPONSE_REPORT_DISMISSED";
    private static final String CHAT_MESSAGE_NOT_FOUND = "AI chat message was not found.";
    private static final int MAX_REPORT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REVIEWER_NOTE_LENGTH = 1000;
    private static final int MAX_REPORT_SNAPSHOT_LENGTH = 4000;
    private static final Set<AiFeatureKey> REPORTABLE_FEATURES = EnumSet.of(
            AiFeatureKey.CHAT_TUTOR,
            AiFeatureKey.XIAOMEI_CONVERSATION,
            AiFeatureKey.QUIZ_GENERATOR,
            AiFeatureKey.STORY_GENERATOR,
            AiFeatureKey.PRACTICE_CHECK,
            // The plan texts SparkBot wrote: the snapshot is what the learner wrote and the AI text.
            AiFeatureKey.LEARNING_PLAN,
            // A hint card: what the hint was for (activity, item, page) and the hint as the learner read it.
            AiFeatureKey.HINT);
    /** My reports is the rail of Ask SparkBot; {@code /student/ai} itself opens Talk with Xiaomei, which has no rail. */
    private static final String STUDENT_REPORTS_PATH = "/student/ai/ask";
    /** The admin AI reports page selects the report its {@code report} query names, as the dashboard links it. */
    private static final String ADMIN_REPORT_PATH = "/admin/ai?report=";

    private final AiResponseReportRepository aiResponseReportRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiPracticeCheckService practiceCheckService;
    private final CurrentAccount currentAccount;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudentAiResponseReportResponse createReport(
            SecurityUserDetails currentUser,
            CreateAiResponseReportRequest request) {
        User student = currentAccount.lock(currentUser);
        AiFeatureKey feature = EnumLookups.tryFrom(AiFeatureKey.class, request.featureKey())
                .filter(REPORTABLE_FEATURES::contains)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI feature key."));
        AiResponseReportReason reason = EnumLookups.tryFrom(AiResponseReportReason.class, request.reasonCategory())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report reason category."));

        AiResponseReport report = new AiResponseReport();
        report.setStudentUser(student);
        report.setFeatureKey(feature);
        report.setReasonCategory(reason);
        report.setDescription(AiInputSanitizer.sanitize(
                PersonalDataRedactor.redact(request.description()), MAX_REPORT_DESCRIPTION_LENGTH));
        // An Ask SparkBot answer and a practice score are kept on the server, so their report always carries the
        // server's copy: a page-made snapshot could claim an answer SparkBot never gave.
        if (feature == AiFeatureKey.CHAT_TUTOR) {
            if (request.chatMessageId() == null) {
                throw new ResponseStatusException(NOT_FOUND, CHAT_MESSAGE_NOT_FOUND);
            }
            attachChatAnswer(report, student, request.chatMessageId());
        } else if (feature == AiFeatureKey.PRACTICE_CHECK) {
            if (request.practiceCheckId() == null) {
                throw new ResponseStatusException(NOT_FOUND, AiPracticeCheckService.CHECK_NOT_FOUND);
            }
            attachPracticeCheck(report, practiceCheckService.requireOwnCheck(student.getId(), request.practiceCheckId()));
        } else {
            report.setPromptSnapshot(snapshot(PersonalDataRedactor.redact(request.promptSnapshot())));
            report.setAiResponseSnapshot(AiInputSanitizer.requireText(
                    request.aiResponseSnapshot(),
                    MAX_REPORT_SNAPSHOT_LENGTH,
                    "AI response snapshot is required when no server-side AI chat message can be resolved."));
        }

        AiResponseReport saved = aiResponseReportRepository.save(report);
        notifyAdmins(saved, student);
        auditLogService.safeRecord(student, "AI_RESPONSE_REPORT_CREATE", TARGET_TYPE, saved.getId(),
                "Student reported an AI answer for review.");
        return toStudentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentAiResponseReportResponse> getCurrentUserReports(
            SecurityUserDetails currentUser, int page, int size) {
        return PageResponse.from(
                PaginationUtils.readPage(PaginationUtils.pageRequest(page, size),
                        request -> aiResponseReportRepository.findByStudentUserIdOrderByCreatedAtDescIdDesc(
                                currentUser.getUserId(), request)),
                AiResponseReportService::toStudentResponse);
    }

    /** The admin queue: every report, open ones first. */
    @Transactional(readOnly = true)
    public PageResponse<AdminAiResponseReportResponse> getAdminReports(int page, int size) {
        return PageResponse.from(
                PaginationUtils.readPage(PaginationUtils.pageRequest(page, size), aiResponseReportRepository::findAllOpenFirst),
                AiResponseReportService::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminAiResponseReportResponse getAdminReport(Long id) {
        return aiResponseReportRepository.findById(id)
                .map(AiResponseReportService::toAdminResponse)
                .orElseThrow(AiResponseReportService::missingReport);
    }

    /** Resolves or dismisses an open report with the note the student reads; the student is notified. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminAiResponseReportResponse reviewReport(
            SecurityUserDetails currentUser,
            Long id,
            ReviewAiResponseReportRequest request) {
        User reviewer = currentAccount.lock(currentUser);
        AiResponseReport report = aiResponseReportRepository.findLockedById(id)
                .orElseThrow(AiResponseReportService::missingReport);
        AiResponseReportStatus decision = EnumLookups.tryFrom(AiResponseReportStatus.class, request.status())
                .filter(status -> status != AiResponseReportStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report review status."));
        if (report.getStatus() != AiResponseReportStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "This AI report is already closed.");
        }
        String reviewerNote = AiInputSanitizer.requireText(
                request.reviewerNote(), MAX_REVIEWER_NOTE_LENGTH, "Reviewer note is required.");

        report.setStatus(decision);
        report.setReviewerUser(reviewer);
        report.setReviewerNote(reviewerNote);
        report.setReviewedAt(LocalDateTime.now());

        // The log names the decision ("AI report resolved" / "dismissed", A5.08); entries written before 17 Nov 2026
        // keep the one action AI_RESPONSE_REPORT_REVIEW for both.
        boolean resolved = decision == AiResponseReportStatus.RESOLVED;
        auditLogService.record(reviewer, resolved ? REVIEW_RESOLVED_ACTION : REVIEW_DISMISSED_ACTION, TARGET_TYPE,
                report.getId(), "Admin " + (resolved ? "resolved" : "dismissed") + " an AI report with a note to the student.");
        notificationService.safeCreateNotification(report.getStudentUser(), reviewedDraft(report, resolved));
        return toAdminResponse(report);
    }

    private static ResponseStatusException missingReport() {
        return new ResponseStatusException(NOT_FOUND, "AI response report was not found.");
    }

    private void attachChatAnswer(AiResponseReport report, User student, Long chatMessageId) {
        AiChatMessage answer = aiChatMessageRepository.findById(chatMessageId)
                .filter(message -> message.getSenderRole() == AiChatSenderRole.ASSISTANT)
                .filter(message -> Objects.equals(message.getSession().getUser().getId(), student.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CHAT_MESSAGE_NOT_FOUND));
        String question = aiChatMessageRepository.findQuestionBefore(answer.getSession().getId(), answer.getId());
        report.setPromptSnapshot(snapshot(question));
        String responseSnapshot = snapshot(answer.getMessageText());
        report.setAiResponseSnapshot(responseSnapshot != null ? responseSnapshot : "-");
    }

    /** What was checked ("Recording · 谢谢 · Lesson title") and the score with its feedback, in plain text. */
    private void attachPracticeCheck(AiResponseReport report, AiPracticeCheck check) {
        AiPracticeCheckService.ReportSnapshot formatted = practiceCheckService.formatReportSnapshot(check);
        report.setPromptSnapshot(snapshot(formatted.prompt()));
        report.setAiResponseSnapshot(snapshot(formatted.response()));
    }

    public static Notification needsReviewDraft(AiResponseReport report, String studentName) {
        String reason = report.getReasonCategory().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return NotificationService.draft(
                NotificationType.ADMIN_TASK,
                "AI report needs review",
                studentName + " reported an AI answer (" + reason + ").",
                TARGET_TYPE,
                report.getId(),
                ADMIN_REPORT_PATH + report.getId());
    }

    public static Notification reviewedDraft(AiResponseReport report, boolean resolved) {
        return NotificationService.draft(
                NotificationType.AI_REPORT_REVIEW,
                "An admin replied to your AI report",
                resolved
                        ? "Your report was resolved. Read the admin's note in AI Helper › Ask SparkBot › My reports."
                        : "Your report was checked and dismissed. Read the admin's note in AI Helper › Ask SparkBot › My reports.",
                TARGET_TYPE,
                report.getId(),
                STUDENT_REPORTS_PATH);
    }

    private void notifyAdmins(AiResponseReport report, User student) {
        String studentName = student.getDisplayName();
        notificationService.notifyActiveAdminsForUserTarget(
                () -> needsReviewDraft(report, studentName),
                TARGET_TYPE + ":" + report.getId(),
                student.getId());
    }

    private static String snapshot(String value) {
        return AiInputSanitizer.sanitize(value, MAX_REPORT_SNAPSHOT_LENGTH);
    }

    private static AdminAiResponseReportResponse toAdminResponse(AiResponseReport report) {
        User student = report.getStudentUser();
        User reviewer = report.getReviewerUser();
        return new AdminAiResponseReportResponse(
                report.getId(),
                student.getDisplayName(),
                student.getEmail(),
                report.getFeatureKey(),
                report.getReasonCategory(),
                report.getDescription(),
                report.getPromptSnapshot(),
                report.getAiResponseSnapshot(),
                report.getStatus(),
                reviewer != null ? reviewer.getDisplayName() : null,
                report.getReviewerNote(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }

    private static StudentAiResponseReportResponse toStudentResponse(AiResponseReport report) {
        return new StudentAiResponseReportResponse(
                report.getId(),
                report.getFeatureKey(),
                report.getReasonCategory(),
                report.getPromptSnapshot(),
                report.getAiResponseSnapshot(),
                report.getStatus(),
                report.getReviewerNote(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }
}
