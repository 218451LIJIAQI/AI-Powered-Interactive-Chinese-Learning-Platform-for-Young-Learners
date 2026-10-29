package com.example.aichinese.service.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.admin.AdminTeacherApplicationResponse;
import com.example.aichinese.dto.admin.AdminTeacherApplicationResponse.HistoryEntry;
import com.example.aichinese.dto.admin.AdminTeacherApplicationResponse.HistoryEvent;
import com.example.aichinese.dto.admin.AdminTeacherApplicationSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.repository.projection.IdCountProjection;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.TransactionalEmailDispatcher;
import com.example.aichinese.service.teacher.TeacherVerificationService;

/**
 * The admin's side of teacher verification: the applications waiting for a decision (oldest first), one application
 * with its documents and history, and the decision. Approving unlocks every teaching tool; requesting changes sends
 * the reason to the teacher, who can replace documents and resubmit.
 */
@Service
public class TeacherApplicationService {

    public static final String ACTION_APPROVE = "ADMIN_TEACHER_APPROVE";
    public static final String ACTION_REQUEST_CHANGES = "ADMIN_TEACHER_REJECT";
    private static final String ACTION_DOCUMENT_VIEW = "ADMIN_TEACHER_DOCUMENT_VIEW";
    private static final String LEGACY_ACTION_RESUBMIT = "TEACHER_VERIFICATION_RESUBMIT";
    private static final int MAX_QUEUE_SIZE = 100;

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository documentRepository;
    private final TeacherVerificationReviewRepository reviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher transactionalEmailDispatcher;
    private final FileStorageService fileStorageService;

    public TeacherApplicationService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository documentRepository,
            TeacherVerificationReviewRepository reviewRepository,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TransactionalEmailDispatcher transactionalEmailDispatcher,
            FileStorageService fileStorageService) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.transactionalEmailDispatcher = transactionalEmailDispatcher;
        this.fileStorageService = fileStorageService;
    }

    /** Applications waiting for a decision, the oldest first (at most the first {@code limit}). */
    @Transactional(readOnly = true)
    public List<AdminTeacherApplicationSummaryResponse> waiting(int limit) {
        List<TeacherProfile> profiles = teacherProfileRepository.findInReview(
                PageRequest.of(0, Math.max(1, Math.min(limit, MAX_QUEUE_SIZE))));
        if (profiles.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> documentCounts = documentRepository
                .countCurrentByTeacherIdIn(profiles.stream().map(TeacherProfile::getId).toList())
                .stream()
                .collect(Collectors.toMap(IdCountProjection::getId, IdCountProjection::getTotal));
        return profiles.stream()
                .map(profile -> new AdminTeacherApplicationSummaryResponse(
                        profile.getId(),
                        profile.getUser().getId(),
                        profile.getUser().getDisplayName(),
                        profile.getUser().getEmail(),
                        profile.getVerificationSubmittedAt(),
                        documentCounts.getOrDefault(profile.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countWaiting() {
        return teacherProfileRepository.countInReview();
    }

    @Transactional(readOnly = true)
    public AdminTeacherApplicationResponse get(Long teacherId) {
        return toResponse(requireProfile(teacherId));
    }

    @Transactional
    public AdminTeacherApplicationResponse approve(SecurityUserDetails currentUser, Long teacherId, String note) {
        User admin = requireActor(currentUser);
        TeacherProfile profile = requireProfile(teacherId);
        TeacherVerificationState state = TeacherVerificationState.of(profile);
        if (state == TeacherVerificationState.APPROVED) {
            return toResponse(profile);
        }
        if (state != TeacherVerificationState.IN_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "Only applications in review can be approved.");
        }

        User teacher = profile.getUser();
        profile.setTeacherStatus(TeacherStatus.APPROVED);
        profile.setRejectionReason(null);
        profile.setVerifiedAt(LocalDateTime.now());
        teacherProfileRepository.save(profile);
        recordReview(profile, admin, TeacherStatus.APPROVED, note);

        notificationService.safeCreateNotification(
                teacher,
                NotificationType.TEACHER_APPROVAL,
                "Your teacher account is approved",
                "Every teaching tool is now open. Start by creating your first class.",
                "TeacherProfile",
                profile.getId(),
                "/teacher/dashboard",
                admin);
        auditLogService.record(admin, ACTION_APPROVE,
                new AuditTarget("TeacherProfile", profile.getId(), teacher.getDisplayName()), null,
                "Admin approved the teacher application.");
        transactionalEmailDispatcher.enqueueTeacherApproval(teacher, note);
        return toResponse(profile);
    }

    /**
     * Sends the application back with a reason the teacher sees on the teaching profile. An approved teacher can also
     * be sent back this way; their teaching tools lock again until an admin approves them.
     */
    @Transactional
    public AdminTeacherApplicationResponse requestChanges(SecurityUserDetails currentUser, Long teacherId, String reason) {
        User admin = requireActor(currentUser);
        TeacherProfile profile = requireProfile(teacherId);
        if (profile.getTeacherStatus() == TeacherStatus.REJECTED) {
            return toResponse(profile);
        }

        User teacher = profile.getUser();
        profile.setTeacherStatus(TeacherStatus.REJECTED);
        profile.setRejectionReason(reason);
        profile.setVerifiedAt(null);
        teacherProfileRepository.save(profile);
        recordReview(profile, admin, TeacherStatus.REJECTED, reason);

        notificationService.safeCreateNotification(
                teacher,
                NotificationType.TEACHER_APPROVAL,
                "Changes requested on your application",
                admin.getDisplayName() + ": " + reason,
                "TeacherProfile",
                profile.getId(),
                "/teacher/profile",
                admin);
        auditLogService.record(admin, ACTION_REQUEST_CHANGES,
                new AuditTarget("TeacherProfile", profile.getId(), teacher.getDisplayName()), reason,
                "Admin requested changes to the teacher application.");
        transactionalEmailDispatcher.enqueueTeacherRejection(teacher, reason);
        return toResponse(profile);
    }

    @Transactional
    public StoredFileDownload getDocument(SecurityUserDetails currentUser, Long documentId) {
        TeacherVerificationDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested teacher document was not found."));
        auditLogService.record(requireActor(currentUser), ACTION_DOCUMENT_VIEW,
                new AuditTarget("TeacherVerificationDocument", documentId,
                        document.getTeacher().getUser().getDisplayName()),
                null, "Admin viewed teacher verification document.");
        return fileStorageService.loadVerificationDocument(document.getUploadedFile());
    }

    private AdminTeacherApplicationResponse toResponse(TeacherProfile profile) {
        User teacher = profile.getUser();
        List<AdminTeacherApplicationResponse.Document> documents = documentRepository
                .findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId()).stream()
                .sorted(Comparator.comparing(TeacherVerificationDocument::getDocumentType))
                .map(document -> {
                    UploadedFile file = document.getUploadedFile();
                    return new AdminTeacherApplicationResponse.Document(
                            document.getId(),
                            document.getDocumentType(),
                            file.getFileName(),
                            file.getFileSize(),
                            document.getSubmittedAt());
                })
                .toList();
        return new AdminTeacherApplicationResponse(
                profile.getId(),
                teacher.getId(),
                teacher.getDisplayName(),
                teacher.getEmail(),
                profile.getBio(),
                TeacherVerificationState.of(profile),
                profile.getVerificationSubmittedAt(),
                documents,
                history(profile));
    }

    /** Account creation, every submission and every decision, newest first. */
    private List<HistoryEntry> history(TeacherProfile profile) {
        User teacher = profile.getUser();
        List<HistoryEntry> entries = new ArrayList<>();
        entries.add(new HistoryEntry(HistoryEvent.ACCOUNT_CREATED, teacher.getDisplayName(), teacher.getCreatedAt(), null));
        for (AuditLog submission : auditLogRepository.findByTargetTypeAndTargetIdAndActionTypeInOrderByCreatedAtAsc(
                "TeacherProfile",
                profile.getId(),
                List.of(TeacherVerificationService.ACTION_APPLICATION_SUBMITTED, LEGACY_ACTION_RESUBMIT))) {
            entries.add(new HistoryEntry(
                    HistoryEvent.APPLICATION_SUBMITTED,
                    submission.getActorUser() == null ? teacher.getDisplayName() : submission.getActorUser().getDisplayName(),
                    submission.getCreatedAt(),
                    null));
        }
        for (TeacherVerificationReview review : reviewRepository.findByTeacherIdOrderByReviewedAtAscIdAsc(profile.getId())) {
            boolean approved = review.getDecisionStatus() == TeacherStatus.APPROVED;
            entries.add(new HistoryEntry(
                    approved ? HistoryEvent.APPROVED : HistoryEvent.CHANGES_REQUESTED,
                    review.getReviewerUser() == null ? null : review.getReviewerUser().getDisplayName(),
                    review.getReviewedAt(),
                    review.getReviewNote()));
        }
        entries.sort(Comparator.comparing(HistoryEntry::at, Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    private void recordReview(TeacherProfile profile, User admin, TeacherStatus decision, String note) {
        TeacherVerificationReview review = new TeacherVerificationReview();
        review.setTeacher(profile);
        review.setDocument(documentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId()).orElse(null));
        review.setReviewerUser(admin);
        review.setDecisionStatus(decision);
        review.setReviewNote(note);
        review.setReviewedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }

    private TeacherProfile requireProfile(Long teacherId) {
        return teacherProfileRepository.findDetailedById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    private User requireActor(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
    }
}
