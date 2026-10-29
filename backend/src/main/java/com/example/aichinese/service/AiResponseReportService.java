package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiReportCountsResponse;
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiPracticeCheck;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;

/**
 * Reports of AI answers: a student reports an Ask SparkBot answer, a Xiaomei reply, a quiz, a story or a practice
 * score, and an admin resolves it (the answer was wrong and the admin acted) or dismisses it (the answer was fine)
 * with a note the student reads in My reports. Each report keeps its own copy of what was asked and answered.
 */
@Service
public class AiResponseReportService {

    private static final String TARGET_TYPE = "AI_RESPONSE_REPORT";
    private static final int MAX_REPORT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REVIEWER_NOTE_LENGTH = 1000;
    private static final int MAX_REPORT_SNAPSHOT_LENGTH = 4000;
    private static final Set<AiFeatureKey> REPORTABLE_FEATURES = EnumSet.of(
            AiFeatureKey.CHAT_TUTOR,
            AiFeatureKey.XIAOMEI_CONVERSATION,
            AiFeatureKey.QUIZ_GENERATOR,
            AiFeatureKey.STORY_GENERATOR,
            AiFeatureKey.PRACTICE_CHECK,
            AiFeatureKey.LEARNING_PLAN);
    private static final String STUDENT_REPORTS_PATH = "/student/ai";
    private static final String ADMIN_REPORTS_PATH = "/admin/ai";

    private final AiResponseReportRepository aiResponseReportRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiPracticeCheckService practiceCheckService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public AiResponseReportService(
            AiResponseReportRepository aiResponseReportRepository,
            AiChatMessageRepository aiChatMessageRepository,
            AiPracticeCheckService practiceCheckService,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.aiResponseReportRepository = aiResponseReportRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
        this.practiceCheckService = practiceCheckService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public StudentAiResponseReportResponse createReport(
            SecurityUserDetails currentUser,
            CreateAiResponseReportRequest request) {
        User student = requireUser(currentUser.getUserId());
        AiFeatureKey feature = AiFeatureKey.tryFrom(request.featureKey())
                .filter(REPORTABLE_FEATURES::contains)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI feature key."));
        AiResponseReportReason reason = AiResponseReportReason.tryFrom(request.reasonCategory())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report reason category."));

        AiResponseReport report = new AiResponseReport();
        report.setStudentUser(student);
        report.setFeatureKey(feature);
        report.setReasonCategory(reason);
        report.setDescription(AiInputSanitizer.sanitize(request.description(), MAX_REPORT_DESCRIPTION_LENGTH));
        report.setStatus(AiResponseReportStatus.PENDING_REVIEW);
        if (feature == AiFeatureKey.CHAT_TUTOR && request.chatMessageId() != null) {
            attachChatAnswer(report, student, request.chatMessageId());
        } else if (feature == AiFeatureKey.PRACTICE_CHECK && request.practiceCheckId() != null) {
            attachPracticeCheck(report, practiceCheckService.requireOwnCheck(student.getId(), request.practiceCheckId()));
        } else {
            report.setPromptSnapshot(snapshot(request.promptSnapshot()));
            report.setAiResponseSnapshot(requireText(
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
                aiResponseReportRepository.findByStudentUserIdOrderByCreatedAtDescIdDesc(
                        currentUser.getUserId(), PaginationUtils.pageRequest(page, size)),
                AiResponseReportService::toStudentResponse);
    }

    /**
     * The admin queue. {@code status}: blank or ALL lists every report with open ones first; OPEN (or
     * PENDING_REVIEW), RESOLVED and DISMISSED narrow it; CLOSED lists resolved and dismissed reports.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminAiResponseReportResponse> getAdminReports(String status, int page, int size) {
        Pageable pageable = PaginationUtils.pageRequest(page, size);
        Set<AiResponseReportStatus> statuses = parseStatusFilter(status);
        Page<AiResponseReport> reports = statuses == null
                ? aiResponseReportRepository.findAllOpenFirst(pageable)
                : aiResponseReportRepository.findByStatusInOrderByCreatedAtDescIdDesc(statuses, pageable);
        return PageResponse.from(reports, AiResponseReportService::toAdminResponse);
    }

    @Transactional(readOnly = true)
    public AdminAiReportCountsResponse getReportCounts() {
        return new AdminAiReportCountsResponse(
                aiResponseReportRepository.countByStatus(AiResponseReportStatus.PENDING_REVIEW),
                aiResponseReportRepository.countByStatus(AiResponseReportStatus.RESOLVED),
                aiResponseReportRepository.countByStatus(AiResponseReportStatus.DISMISSED));
    }

    /** Resolves or dismisses an open report with the note the student reads; the student is notified. */
    @Transactional
    public AdminAiResponseReportResponse reviewReport(
            SecurityUserDetails currentUser,
            Long id,
            ReviewAiResponseReportRequest request) {
        User reviewer = requireUser(currentUser.getUserId());
        AiResponseReport report = aiResponseReportRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI response report was not found."));
        AiResponseReportStatus decision = AiResponseReportStatus.tryFrom(request.status())
                .filter(status -> status != AiResponseReportStatus.PENDING_REVIEW)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report review status."));
        if (report.getStatus() != AiResponseReportStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "This AI report is already closed.");
        }

        report.setStatus(decision);
        report.setReviewerUser(reviewer);
        report.setReviewerNote(requireText(request.reviewerNote(), MAX_REVIEWER_NOTE_LENGTH, "Reviewer note is required."));
        report.setReviewedAt(LocalDateTime.now());

        auditLogService.record(reviewer, "AI_RESPONSE_REPORT_REVIEW", TARGET_TYPE, report.getId(),
                "Admin " + (decision == AiResponseReportStatus.RESOLVED ? "resolved" : "dismissed")
                        + " an AI report with a note to the student.");
        notificationService.safeCreateNotification(
                report.getStudentUser(),
                NotificationType.AI_REPORT_REVIEW,
                "An admin replied to your AI report",
                decision == AiResponseReportStatus.RESOLVED
                        ? "Your report was resolved. Read the admin's note in AI Helper, My reports."
                        : "Your report was checked and dismissed. Read the admin's note in AI Helper, My reports.",
                TARGET_TYPE,
                report.getId(),
                STUDENT_REPORTS_PATH,
                reviewer);
        return toAdminResponse(report);
    }

    private void attachChatAnswer(AiResponseReport report, User student, Long chatMessageId) {
        AiChatMessage answer = aiChatMessageRepository.findById(chatMessageId)
                .filter(message -> message.getSenderRole() == AiChatSenderRole.ASSISTANT)
                .filter(message -> Objects.equals(message.getSession().getUser().getId(), student.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat message was not found."));
        report.setChatSession(answer.getSession());
        report.setChatMessage(answer);
        String question = aiChatMessageRepository
                .findFirstBySessionIdAndSenderRoleAndIdLessThanOrderByIdDesc(
                        answer.getSession().getId(), AiChatSenderRole.USER, answer.getId())
                .map(AiChatMessage::getMessageText)
                .orElse(null);
        report.setPromptSnapshot(snapshot(question));
        report.setAiResponseSnapshot(snapshot(answer.getMessageText()));
    }

    /** What was checked ("Recording · 谢谢 · Lesson title") and the score with its feedback, in plain text. */
    private static void attachPracticeCheck(AiResponseReport report, AiPracticeCheck check) {
        String kind = check.getCheckType() == PracticeCheckType.RECORDING ? "Recording" : "Handwriting";
        report.setPromptSnapshot(snapshot(kind + " · " + check.getTargetText()
                + (check.getLesson() == null ? "" : " · " + check.getLesson().getTitle())));
        List<String> lines = new ArrayList<>();
        lines.add(check.getScore() + " / 100 · " + check.getHeadline());
        if (check.getCheckType() == PracticeCheckType.RECORDING) {
            lines.add("Tones " + scoreText(check.getTonesScore()) + " · Clarity " + scoreText(check.getClarityScore())
                    + " · Pace " + scoreText(check.getPaceScore()));
        }
        if (check.getFeedback() != null) {
            lines.add(check.getFeedback());
        }
        if (check.getTips() != null) {
            for (String tip : check.getTips().split("\n")) {
                lines.add("- " + tip);
            }
        }
        report.setAiResponseSnapshot(snapshot(String.join("\n", lines)));
    }

    private static String scoreText(Integer score) {
        return score == null ? "-" : score.toString();
    }

    private void notifyAdmins(AiResponseReport report, User student) {
        String reason = report.getReasonCategory().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        userRepository.findAllByRoleName(RoleName.ADMIN).forEach(admin ->
                notificationService.safeCreateNotification(
                        admin,
                        NotificationType.ADMIN_TASK,
                        "AI report needs review",
                        student.getDisplayName() + " reported an AI answer (" + reason + ").",
                        TARGET_TYPE,
                        report.getId(),
                        ADMIN_REPORTS_PATH,
                        student));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private static Set<AiResponseReportStatus> parseStatusFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.strip())) {
            return null;
        }
        String key = value.strip().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(key)) {
            return EnumSet.of(AiResponseReportStatus.PENDING_REVIEW);
        }
        if ("CLOSED".equals(key)) {
            return EnumSet.of(AiResponseReportStatus.RESOLVED, AiResponseReportStatus.DISMISSED);
        }
        return AiResponseReportStatus.tryFrom(key)
                .map(EnumSet::of)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report status."));
    }

    private static String snapshot(String value) {
        return AiInputSanitizer.sanitize(value, MAX_REPORT_SNAPSHOT_LENGTH);
    }

    private static String requireText(String value, int maxLength, String message) {
        String sanitized = AiInputSanitizer.sanitize(value, maxLength);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }

    private static AdminAiResponseReportResponse toAdminResponse(AiResponseReport report) {
        User student = report.getStudentUser();
        User reviewer = report.getReviewerUser();
        return new AdminAiResponseReportResponse(
                report.getId(),
                student.getDisplayName(),
                student.getEmail(),
                report.getFeatureKey().name(),
                report.getReasonCategory().name(),
                report.getDescription(),
                report.getPromptSnapshot(),
                reportedAnswer(report),
                report.getStatus().name(),
                reviewer != null ? reviewer.getDisplayName() : null,
                report.getReviewerNote(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }

    private static StudentAiResponseReportResponse toStudentResponse(AiResponseReport report) {
        return new StudentAiResponseReportResponse(
                report.getId(),
                report.getFeatureKey().name(),
                report.getReasonCategory().name(),
                report.getDescription(),
                report.getPromptSnapshot(),
                reportedAnswer(report),
                report.getStatus().name(),
                report.getReviewerNote(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }

    /** Older chat reports kept only the link to the answer; newer ones keep their own copy. */
    private static String reportedAnswer(AiResponseReport report) {
        if (report.getAiResponseSnapshot() != null || report.getChatMessage() == null) {
            return report.getAiResponseSnapshot();
        }
        return snapshot(report.getChatMessage().getMessageText());
    }
}
