package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.AdminAiResponseReportResponse;
import com.example.aichinese.dto.ai.CreateAiResponseReportRequest;
import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.dto.ai.StudentAiResponseReportResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;

@Service
public class AiResponseReportService {

    private static final String TARGET_TYPE = "AI_RESPONSE_REPORT";
    private static final String RELATED_ENTITY_CHAT_SESSION = "AiChatSession";
    private static final int MAX_REPORT_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_REVIEWER_NOTE_LENGTH = 1000;
    private static final int MAX_REPORT_SNAPSHOT_LENGTH = 4000;

    private final AiResponseReportRepository aiResponseReportRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public AiResponseReportService(
            AiResponseReportRepository aiResponseReportRepository,
            AiUsageLogRepository aiUsageLogRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.aiResponseReportRepository = Objects.requireNonNull(aiResponseReportRepository, "aiResponseReportRepository");
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.aiChatSessionRepository = Objects.requireNonNull(aiChatSessionRepository, "aiChatSessionRepository");
        this.aiChatMessageRepository = Objects.requireNonNull(aiChatMessageRepository, "aiChatMessageRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional
    public StudentAiResponseReportResponse createReport(
            SecurityUserDetails currentUser,
            CreateAiResponseReportRequest request) {
        requireRole(currentUser, RoleName.STUDENT, "Only students can report AI responses.");
        User student = requireUser(currentUser.getUserId());
        AiFeatureKey featureKey = parseFeatureKey(request.featureKey());
        AiResponseReportReason reason = parseReportReason(request.reasonCategory());
        ReportContext context = resolveReportContext(student, request, featureKey);

        AiResponseReport report = new AiResponseReport();
        report.setStudentUser(student);
        report.setAiUsageLog(context.usageLog());
        report.setChatSession(context.chatSession());
        report.setChatMessage(context.chatMessage());
        report.setFeatureKey(featureKey.name());
        report.setReasonCategory(reason);
        report.setDescription(normalizeReportText(request.description(), MAX_REPORT_DESCRIPTION_LENGTH));
        report.setPromptSnapshot(context.promptSnapshot());
        report.setAiResponseSnapshot(context.aiResponseSnapshot());
        report.setStatus(AiResponseReportStatus.PENDING_REVIEW);

        AiResponseReport saved = aiResponseReportRepository.save(report);
        notifyAdmins(saved, student);
        auditLogService.safeRecord(
                student,
                "AI_RESPONSE_REPORT_CREATE",
                TARGET_TYPE,
                saved.getId(),
                "Student reported an AI response for review.");
        return toStudentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentAiResponseReportResponse> getCurrentUserReports(SecurityUserDetails currentUser, int page, int size) {
        requireRole(currentUser, RoleName.STUDENT, "Only students can view their AI reports.");
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<AiResponseReport> reportPage = aiResponseReportRepository.findByStudentUserIdOrderByCreatedAtDesc(
                currentUser.getUserId(),
                newestPageable(safePage, safeSize));
        return mapStudentReportPage(reportPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAiResponseReportResponse> getAdminReports(String status, int page, int size) {
        AiResponseReportStatus parsedStatus = parseOptionalStatus(status);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<AiResponseReport> reportPage = parsedStatus == null
                ? aiResponseReportRepository.findAll(newestPageable(safePage, safeSize))
                : aiResponseReportRepository.findByStatusOrderByCreatedAtDesc(parsedStatus, newestPageable(safePage, safeSize));
        return mapAdminReportPage(reportPage, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminAiResponseReportResponse getAdminReport(Long id) {
        return toAdminResponse(requireReport(id));
    }

    @Transactional
    public AdminAiResponseReportResponse reviewReport(
            SecurityUserDetails currentUser,
            Long id,
            ReviewAiResponseReportRequest request) {
        User reviewer = requireUser(currentUser.getUserId());
        AiResponseReport report = requireLockedReport(id);
        AiResponseReportStatus newStatus = parseReportStatus(request.status());

        if (newStatus == AiResponseReportStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "Review status must move the report forward.");
        }
        if (isTerminal(report.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "This AI report is already closed.");
        }

        report.setStatus(newStatus);
        report.setReviewerUser(reviewer);
        report.setReviewerNote(requireReportText(
                request.reviewerNote(),
                MAX_REVIEWER_NOTE_LENGTH,
                "Reviewer note is required."));
        report.setReviewedAt(LocalDateTime.now());

        auditLogService.record(
                reviewer,
                "AI_RESPONSE_REPORT_REVIEW",
                TARGET_TYPE,
                report.getId(),
                "Admin reviewed an AI response report with status " + newStatus.name() + ".");

        if (newStatus.isTerminal()) {
            notificationService.safeCreateNotification(
                    report.getStudentUser(),
                    NotificationType.AI_REPORT_REVIEW,
                    "AI report reviewed",
                    "Your AI response report has been reviewed: " + newStatus.name() + ".",
                    TARGET_TYPE,
                    report.getId(),
                    "/student/ai-helper",
                    reviewer);
        }

        return toAdminResponse(report);
    }

    private ReportContext resolveReportContext(
            User student,
            CreateAiResponseReportRequest request,
            AiFeatureKey featureKey) {
        AiUsageLog usageLog = resolveOwnedUsageLog(student, request.aiUsageLogId(), featureKey);
        AiChatSession chatSession = resolveOwnedChatSession(student, request.chatSessionId());
        AiChatMessage chatMessage = resolveOwnedAssistantMessage(student, request.chatMessageId());

        chatSession = resolveUsageLogChatSession(student, usageLog, chatSession);
        if (chatMessage != null) {
            chatSession = requireMessageSession(chatMessage, chatSession);
        } else if (chatSession != null) {
            chatMessage = findLatestAssistantMessage(chatSession);
        }

        if (chatMessage != null) {
            return new ReportContext(usageLog, chatSession, chatMessage, null, null);
        }
        return new ReportContext(
                usageLog,
                chatSession,
                null,
                normalizeSnapshot(request.promptSnapshot()),
                requireReportText(
                        request.aiResponseSnapshot(),
                        MAX_REPORT_SNAPSHOT_LENGTH,
                        "AI response snapshot is required when no server-side AI chat message can be resolved."));
    }

    private AiChatSession resolveUsageLogChatSession(
            User student,
            AiUsageLog usageLog,
            AiChatSession selectedChatSession) {
        if (selectedChatSession != null
                || usageLog == null
                || !RELATED_ENTITY_CHAT_SESSION.equals(usageLog.getRelatedEntityType())
                || usageLog.getRelatedEntityId() == null) {
            return selectedChatSession;
        }
        return aiChatSessionRepository.findByIdAndUserId(usageLog.getRelatedEntityId(), student.getId())
                .orElse(null);
    }

    private static AiChatSession requireMessageSession(AiChatMessage chatMessage, AiChatSession selectedChatSession) {
        AiChatSession messageSession = chatMessage.getSession();
        if (selectedChatSession != null && !messageSession.getId().equals(selectedChatSession.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Chat message does not belong to the selected chat session.");
        }
        return messageSession;
    }

    private AiChatMessage findLatestAssistantMessage(AiChatSession chatSession) {
        return aiChatMessageRepository
                .findFirstBySessionIdAndSenderRoleOrderByIdDesc(chatSession.getId(), AiChatSenderRole.ASSISTANT)
                .orElse(null);
    }

    private AiUsageLog resolveOwnedUsageLog(User student, Long usageLogId, AiFeatureKey featureKey) {
        if (usageLogId == null) {
            return null;
        }
        AiUsageLog usageLog = aiUsageLogRepository.findByIdAndUserId(usageLogId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI usage log was not found for this student."));
        if (!featureKey.name().equals(usageLog.getFeatureKey())) {
            throw new ResponseStatusException(BAD_REQUEST, "AI feature key does not match the selected usage log.");
        }
        return usageLog;
    }

    private AiChatSession resolveOwnedChatSession(User student, Long chatSessionId) {
        if (chatSessionId == null) {
            return null;
        }
        return aiChatSessionRepository.findByIdAndUserId(chatSessionId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat session was not found for this student."));
    }

    private AiChatMessage resolveOwnedAssistantMessage(User student, Long chatMessageId) {
        if (chatMessageId == null) {
            return null;
        }
        AiChatMessage chatMessage = aiChatMessageRepository.findByIdAndSessionUserId(chatMessageId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI chat message was not found for this student."));
        if (chatMessage.getSenderRole() != AiChatSenderRole.ASSISTANT) {
            throw new ResponseStatusException(BAD_REQUEST, "Only AI assistant messages can be reported.");
        }
        return chatMessage;
    }

    private static String normalizeSnapshot(String value) {
        return normalizeReportText(value, MAX_REPORT_SNAPSHOT_LENGTH);
    }

    private static String normalizeReportText(String value, int maxLength) {
        return AiInputSanitizer.sanitize(value, maxLength);
    }

    private static String requireReportText(String value, int maxLength, String message) {
        String sanitized = normalizeReportText(value, maxLength);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return sanitized;
    }

    private void notifyAdmins(AiResponseReport report, User student) {
        userRepository.findAllByRoleName(RoleName.ADMIN).forEach(admin ->
                notificationService.safeCreateNotification(
                        admin,
                        NotificationType.ADMIN_TASK,
                        "AI response report needs review",
                        student.getDisplayName() + " reported an AI response as " + report.getReasonCategory().name() + ".",
                        TARGET_TYPE,
                        report.getId(),
                        "/admin/moderation",
                        student));
    }

    private AiResponseReport requireReport(Long id) {
        return aiResponseReportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI response report was not found."));
    }

    private AiResponseReport requireLockedReport(Long id) {
        return aiResponseReportRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "AI response report was not found."));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private static void requireRole(SecurityUserDetails currentUser, RoleName roleName, String message) {
        if (currentUser == null || currentUser.getRoleNames().stream().noneMatch(role -> role.equals(roleName.name()))) {
            throw new ResponseStatusException(FORBIDDEN, message);
        }
    }

    private static AiResponseReportStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AiResponseReportStatus.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report status."));
    }

    private static AiFeatureKey parseFeatureKey(String value) {
        return AiFeatureKey.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI feature key."));
    }

    private static AiResponseReportReason parseReportReason(String value) {
        return AiResponseReportReason.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report reason category."));
    }

    private static AiResponseReportStatus parseReportStatus(String value) {
        return AiResponseReportStatus.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid AI report review status."));
    }

    private static boolean isTerminal(AiResponseReportStatus status) {
        return status != null && status.isTerminal();
    }

    private AdminAiResponseReportResponse toAdminResponse(AiResponseReport report) {
        return toAdminResponse(report, loadPromptSnapshots(List.of(report)));
    }

    private AdminAiResponseReportResponse toAdminResponse(
            AiResponseReport report,
            Map<Long, String> promptSnapshotsByChatMessageId) {
        User student = report.getStudentUser();
        User reviewer = report.getReviewerUser();
        ResolvedReportSnapshots resolvedSnapshots = resolveReportSnapshots(report, promptSnapshotsByChatMessageId);
        return new AdminAiResponseReportResponse(
                report.getId(),
                student != null ? student.getId() : null,
                student != null ? student.getDisplayName() : null,
                student != null ? student.getEmail() : null,
                report.getAiUsageLog() != null ? report.getAiUsageLog().getId() : null,
                report.getChatSession() != null ? report.getChatSession().getId() : null,
                report.getChatMessage() != null ? report.getChatMessage().getId() : null,
                report.getFeatureKey(),
                report.getReasonCategory() != null ? report.getReasonCategory().name() : null,
                report.getDescription(),
                resolvedSnapshots.promptSnapshot(),
                resolvedSnapshots.aiResponseSnapshot(),
                report.getStatus() != null ? report.getStatus().name() : null,
                reviewer != null ? reviewer.getId() : null,
                reviewer != null ? reviewer.getDisplayName() : null,
                report.getReviewerNote(),
                report.getReviewedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    private StudentAiResponseReportResponse toStudentResponse(AiResponseReport report) {
        return toStudentResponse(report, loadPromptSnapshots(List.of(report)));
    }

    private StudentAiResponseReportResponse toStudentResponse(
            AiResponseReport report,
            Map<Long, String> promptSnapshotsByChatMessageId) {
        ResolvedReportSnapshots resolvedSnapshots = resolveReportSnapshots(report, promptSnapshotsByChatMessageId);
        return new StudentAiResponseReportResponse(
                report.getId(),
                report.getAiUsageLog() != null ? report.getAiUsageLog().getId() : null,
                report.getChatSession() != null ? report.getChatSession().getId() : null,
                report.getChatMessage() != null ? report.getChatMessage().getId() : null,
                report.getFeatureKey(),
                report.getReasonCategory() != null ? report.getReasonCategory().name() : null,
                report.getDescription(),
                resolvedSnapshots.promptSnapshot(),
                resolvedSnapshots.aiResponseSnapshot(),
                report.getStatus() != null ? report.getStatus().name() : null,
                report.getReviewedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    private static Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResponse<AdminAiResponseReportResponse> mapAdminReportPage(Page<AiResponseReport> reportPage, int page, int size) {
        Map<Long, String> promptSnapshotsByChatMessageId = loadPromptSnapshots(reportPage.getContent());
        return new PageResponse<>(
                reportPage.getContent().stream()
                        .map(report -> toAdminResponse(report, promptSnapshotsByChatMessageId))
                        .toList(),
                page,
                size,
                reportPage.getTotalElements(),
                PaginationUtils.totalPages(reportPage.getTotalElements(), size));
    }

    private PageResponse<StudentAiResponseReportResponse> mapStudentReportPage(Page<AiResponseReport> reportPage, int page, int size) {
        Map<Long, String> promptSnapshotsByChatMessageId = loadPromptSnapshots(reportPage.getContent());
        return new PageResponse<>(
                reportPage.getContent().stream()
                        .map(report -> toStudentResponse(report, promptSnapshotsByChatMessageId))
                        .toList(),
                page,
                size,
                reportPage.getTotalElements(),
                PaginationUtils.totalPages(reportPage.getTotalElements(), size));
    }

    private static ResolvedReportSnapshots resolveReportSnapshots(
            AiResponseReport report,
            Map<Long, String> promptSnapshotsByChatMessageId) {
        AiChatMessage chatMessage = report.getChatMessage();
        if (chatMessage == null) {
            return new ResolvedReportSnapshots(report.getPromptSnapshot(), report.getAiResponseSnapshot());
        }
        return new ResolvedReportSnapshots(
                promptSnapshotsByChatMessageId.get(chatMessage.getId()),
                normalizeSnapshot(chatMessage.getMessageText()));
    }

    private Map<Long, String> loadPromptSnapshots(List<AiResponseReport> reports) {
        List<Long> assistantMessageIds = reports.stream()
                .map(AiResponseReport::getChatMessage)
                .filter(Objects::nonNull)
                .map(AiChatMessage::getId)
                .distinct()
                .toList();
        if (assistantMessageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> promptSnapshotsByChatMessageId = new LinkedHashMap<>();
        aiChatMessageRepository.findPromptSnapshotsForAssistantMessageIds(assistantMessageIds).forEach(projection ->
                promptSnapshotsByChatMessageId.put(
                        projection.getAssistantMessageId(),
                        normalizeSnapshot(projection.getPromptSnapshot())));
        return promptSnapshotsByChatMessageId;
    }

    private record ResolvedReportSnapshots(String promptSnapshot, String aiResponseSnapshot) {
    }

    private record ReportContext(
            AiUsageLog usageLog,
            AiChatSession chatSession,
            AiChatMessage chatMessage,
            String promptSnapshot,
            String aiResponseSnapshot) {
    }
}
