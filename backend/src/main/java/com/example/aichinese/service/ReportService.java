package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.AdminReportActionRequest;
import com.example.aichinese.dto.communication.CreateReportRequest;
import com.example.aichinese.dto.communication.ReportResponse;
import com.example.aichinese.entity.Report;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.ReportStatus;
import com.example.aichinese.repository.ReportRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CommunicationTargetResolver communicationTargetResolver;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ReportService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            CommunicationTargetResolver communicationTargetResolver,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.communicationTargetResolver = communicationTargetResolver;
        this.communicationMapper = communicationMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ReportResponse createReport(SecurityUserDetails currentUser, CreateReportRequest request) {
        User reporter = requireUser(currentUser.getUserId());
        CommunicationTargetResolver.ResolvedTarget target = communicationTargetResolver.resolveReportTarget(
                request.targetType(),
                request.targetId());

        // Anti-spam: refuse a verbatim-identical PENDING report from the same
        // reporter against the same target. Without this guard a misbehaving
        // or accidental retry of the exact same report would flood every
        // admin's notification feed (admin notification fan-out below loops
        // over all admins). Distinct concerns on the same target ("wrong
        // translation" vs "unclear instructions") remain allowed because the
        // reason text differs.
        String trimmedReason = request.reason().trim();
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndReasonAndReportStatus(
                reporter.getId(),
                target.targetType(),
                target.targetId(),
                trimmedReason,
                ReportStatus.PENDING)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "You already have a pending report with this exact reason. Please wait for an admin decision.");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(target.targetType());
        report.setTargetId(target.targetId());
        report.setReason(trimmedReason);
        report.setRelatedTeacher(target.relatedTeacher());
        report.setReportStatus(ReportStatus.PENDING);
        report.setTeacherRevisionRequested(false);
        report.setAdminNote(null);
        Report saved = reportRepository.save(report);

        for (User admin : userRepository.findAllByRoleName(RoleName.ADMIN)) {
            notificationService.safeCreateNotification(
                    admin,
                    NotificationType.ADMIN_TASK,
                    "New report submitted",
                    "A new report requires admin review.",
                    saved.getTargetType(),
                    saved.getTargetId(),
                    "/admin/reports",
                    reporter);
        }

        notificationService.safeCreateNotification(
                reporter,
                NotificationType.REPORT_CREATED,
                "Report submitted",
                "Your report has been submitted for admin review.",
                saved.getTargetType(),
                saved.getTargetId(),
                "/student/reports",
                reporter);

        return communicationMapper.toReportResponse(saved, target.targetTitle(), false);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getMyReports(SecurityUserDetails currentUser) {
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(report -> communicationMapper.toReportResponse(report, resolveTitle(report), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getMyReport(SecurityUserDetails currentUser, Long reportId) {
        Report report = reportRepository.findByIdAndReporterId(reportId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Report was not found."));
        return communicationMapper.toReportResponse(report, resolveTitle(report), false);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getAdminReports() {
        // Delegate ordering to the database (uses the BaseEntity created_at
        // column) so admins no longer pay an O(N log N) JVM sort over the
        // full report set on every page load.
        return reportRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(report -> communicationMapper.toReportResponse(report, resolveTitle(report), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getAdminReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Report was not found."));
        return communicationMapper.toReportResponse(report, resolveTitle(report), true);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getTeacherReports(SecurityUserDetails currentUser) {
        return reportRepository.findByRelatedTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(report -> communicationMapper.toReportResponse(report, resolveTitle(report), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse getTeacherReport(SecurityUserDetails currentUser, Long reportId) {
        Report report = reportRepository.findById(reportId)
                .filter(item -> item.getRelatedTeacher() != null
                        && item.getRelatedTeacher().getUser() != null
                        && item.getRelatedTeacher().getUser().getId().equals(currentUser.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Report was not found."));
        return communicationMapper.toReportResponse(report, resolveTitle(report), false);
    }

    @Transactional
    public ReportResponse reviewReport(SecurityUserDetails currentUser, Long reportId, AdminReportActionRequest request) {
        return updateStatus(currentUser, reportId, ReportStatus.REVIEWED, request, false, "REPORT_REVIEWED");
    }

    @Transactional
    public ReportResponse dismissReport(SecurityUserDetails currentUser, Long reportId, AdminReportActionRequest request) {
        return updateStatus(currentUser, reportId, ReportStatus.DISMISSED, request, false, "REPORT_DISMISSED");
    }

    @Transactional
    public ReportResponse markActionTaken(SecurityUserDetails currentUser, Long reportId, AdminReportActionRequest request) {
        return updateStatus(currentUser, reportId, ReportStatus.ACTION_TAKEN, request, false, "REPORT_ACTION_TAKEN");
    }

    @Transactional
    public ReportResponse requestRevision(SecurityUserDetails currentUser, Long reportId, AdminReportActionRequest request) {
        return updateStatus(currentUser, reportId, ReportStatus.ACTION_TAKEN, request, true, "REPORT_REVISION_REQUESTED");
    }

    private ReportResponse updateStatus(
            SecurityUserDetails currentUser,
            Long reportId,
            ReportStatus status,
            AdminReportActionRequest request,
            boolean revisionRequested,
            String actionType) {
        User admin = requireUser(currentUser.getUserId());
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Report was not found."));
        report.setReportStatus(status);
        report.setAdminNote(request != null && request.note() != null && !request.note().isBlank() ? request.note().trim() : null);
        report.setTeacherRevisionRequested(revisionRequested);

        if (revisionRequested && report.getRelatedTeacher() != null && report.getRelatedTeacher().getUser() != null) {
            notificationService.safeCreateNotification(
                    report.getRelatedTeacher().getUser(),
                    NotificationType.MODERATION_ACTION,
                    "Revision requested",
                    "An admin has requested a revision related to your content report.",
                    report.getTargetType(),
                    report.getTargetId(),
                    "/teacher/reports",
                    admin);
        }

        if (report.getReporter() != null) {
            notificationService.safeCreateNotification(
                    report.getReporter(),
                    NotificationType.REPORT_CREATED,
                    "Report updated",
                    "Your report status has been updated to " + status.name() + ".",
                    report.getTargetType(),
                    report.getTargetId(),
                    "/student/reports",
                    admin);
        }

        auditLogService.safeRecord(
                admin,
                actionType,
                report.getTargetType(),
                report.getTargetId(),
                "Admin updated report #" + report.getId() + " to " + status.name() + ".",
                null);

        return communicationMapper.toReportResponse(report, resolveTitle(report), true);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private String resolveTitle(Report report) {
        try {
            return communicationTargetResolver.resolveReportTarget(report.getTargetType(), report.getTargetId()).targetTitle();
        } catch (ResponseStatusException exception) {
            return null;
        }
    }
}
