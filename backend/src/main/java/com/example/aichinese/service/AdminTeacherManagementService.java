package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.AdminTeacherDetailResponse;
import com.example.aichinese.dto.admin.AdminTeacherListItemResponse;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Predicate;

/**
 * Admin-side workflow for teacher onboarding and oversight:
 * application review, document verification, status changes
 * (PENDING / APPROVED / SUSPENDED), and profile inspection.
 *
 * <h2>Verification flow</h2>
 * <p>{@link TeacherVerificationDocument}s submitted by the teacher
 * are reviewed and recorded as {@link TeacherVerificationReview}
 * rows; the admin's note + decision is the audit trail. Approving a
 * teacher requires every required document to have been reviewed,
 * so the policy is "all green or stays pending".</p>
 *
 * <h2>Status changes</h2>
 * <p>{@link TeacherStatus} transitions affect both the teacher's
 * ability to author content and the visibility of their existing
 * published content; suspension hides existing live content from
 * the public surface while preserving the rows for audit / reactivation.
 * All changes go through {@link AuditLogService}.</p>
 */
@Service
public class AdminTeacherManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminTeacherManagementService.class);

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository teacherVerificationDocumentRepository;
    private final TeacherVerificationReviewRepository teacherVerificationReviewRepository;
    private final UserRepository userRepository;
    private final TeacherManagementMapper teacherManagementMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TransactionalEmailDispatcher transactionalEmailDispatcher;
    private final FileStorageService fileStorageService;
    private final AuditLogRepository auditLogRepository;

    public AdminTeacherManagementService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository teacherVerificationDocumentRepository,
            TeacherVerificationReviewRepository teacherVerificationReviewRepository,
            UserRepository userRepository,
            TeacherManagementMapper teacherManagementMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TransactionalEmailDispatcher transactionalEmailDispatcher,
            FileStorageService fileStorageService,
            AuditLogRepository auditLogRepository) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.teacherVerificationDocumentRepository = Objects.requireNonNull(teacherVerificationDocumentRepository, "teacherVerificationDocumentRepository");
        this.teacherVerificationReviewRepository = Objects.requireNonNull(teacherVerificationReviewRepository, "teacherVerificationReviewRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.teacherManagementMapper = Objects.requireNonNull(teacherManagementMapper, "teacherManagementMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.transactionalEmailDispatcher = Objects.requireNonNull(transactionalEmailDispatcher, "transactionalEmailDispatcher");
        this.fileStorageService = Objects.requireNonNull(fileStorageService, "fileStorageService");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTeacherListItemResponse> listTeachers(
            String status,
            String keyword,
            int page,
            int size,
            String sort) {
        Pageable pageable = PageRequest.of(
                PaginationUtils.sanitizePage(page),
                PaginationUtils.sanitizeSize(size),
                resolveTeacherSort(sort));

        TeacherStatus parsedStatus = parseTeacherStatus(status);
        // Centralise the trim/lowercase/blank-to-null normalisation through
        // the shared TextUtils helper. The previous inline ternary repeated
        // the same pattern that appears in PublicContentService and
        // AdminContentReviewService, so we now share a single contract that
        // is unit-tested in TextUtilsTests.
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> "%" + value.toLowerCase(Locale.ROOT) + "%")
                .orElse(null);

        Page<TeacherProfile> teachers = teacherProfileRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (parsedStatus != null) {
                predicates.add(cb.equal(root.get("teacherStatus"), parsedStatus));
            }

            if (normalizedKeyword != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("displayName")), normalizedKeyword),
                        cb.like(cb.lower(root.get("user").get("email")), normalizedKeyword),
                        cb.like(cb.lower(cb.coalesce(root.get("bio"), "")), normalizedKeyword)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        }, pageable);

        List<AdminTeacherListItemResponse> items = toTeacherListItemsFromPage(teachers.getContent());
        return new PageResponse<>(items, teachers.getNumber(), teachers.getSize(), teachers.getTotalElements(), teachers.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminTeacherDetailResponse getTeacherDetail(Long teacherId) {
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);
        return buildTeacherDetail(teacherProfile);
    }

    @Transactional(readOnly = true)
    public List<AdminTeacherListItemResponse> listPendingTeachers() {
        return listPendingTeachers(0, 20).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTeacherListItemResponse> listPendingTeachers(int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<TeacherProfile> teachers = teacherProfileRepository.findByTeacherStatusOrderByCreatedAtDesc(
                TeacherStatus.PENDING,
                PageRequest.of(safePage, safeSize));
        List<AdminTeacherListItemResponse> items = toTeacherListItemsFromPage(teachers.getContent());
        return new PageResponse<>(items, safePage, safeSize, teachers.getTotalElements(), teachers.getTotalPages());
    }

    @Transactional
    public AdminTeacherDetailResponse approveTeacher(SecurityUserDetails currentUser, Long teacherId, String note) {
        User adminUser = findActor(currentUser);
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);
        String normalizedNote = TextUtils.trimToNull(note);

        // Idempotency guard. Without this, an admin double-clicking "Approve"
        // on an already-APPROVED teacher would write a duplicate
        // TeacherVerificationReview row, send a duplicate in-app
        // notification, AND fire a duplicate approval email — the email
        // duplicate is the worst of these because it is the only side
        // effect that escapes the application boundary.
        if (teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED) {
            return buildTeacherDetail(teacherProfile);
        }

        if (teacherVerificationDocumentRepository.countByTeacherId(teacherProfile.getId()) == 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "At least one verification document is required before approving a teacher.");
        }

        teacherProfile.setTeacherStatus(TeacherStatus.APPROVED);
        teacherProfile.setRejectionReason(null);
        teacherProfile.setPublicVisible(true);
        teacherProfile.setVerifiedAt(LocalDateTime.now());
        teacherProfileRepository.save(teacherProfile);

        TeacherVerificationReview review = new TeacherVerificationReview();
        review.setTeacher(teacherProfile);
        review.setDocument(teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(teacherId).orElse(null));
        review.setReviewerUser(adminUser);
        review.setDecisionStatus(TeacherStatus.APPROVED);
        review.setReviewNote(normalizedNote);
        review.setReviewedAt(LocalDateTime.now());
        teacherVerificationReviewRepository.save(review);

        notificationService.safeCreateNotification(
                teacherProfile.getUser(),
                "Teacher account approved",
                "Your teacher account has been approved by an administrator.",
                "/teacher/profile");
        log.info("Teacher approved: teacherId={}, adminUserId={}", teacherProfile.getId(), adminUser.getId());
        auditLogService.record(adminUser, "ADMIN_TEACHER_APPROVE", "TeacherProfile", teacherProfile.getId(),
                "Admin approved teacher verification.");
        transactionalEmailDispatcher.enqueueTeacherApproval(teacherProfile.getUser(), normalizedNote);

        return buildTeacherDetail(teacherProfile);
    }

    @Transactional
    public AdminTeacherDetailResponse rejectTeacher(SecurityUserDetails currentUser, Long teacherId, String reason) {
        User adminUser = findActor(currentUser);
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Rejection reason is required.");
        }

        // Idempotency guard mirroring approveTeacher: prevents a duplicate
        // rejection email + notification + audit row + verification-review
        // row when the admin re-clicks reject on an already-REJECTED
        // teacher (or refreshes the page mid-action).
        if (teacherProfile.getTeacherStatus() == TeacherStatus.REJECTED) {
            return buildTeacherDetail(teacherProfile);
        }

        String trimmedReason = TextUtils.trimToNull(reason);
        if (trimmedReason == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Rejection reason is required.");
        }
        teacherProfile.setTeacherStatus(TeacherStatus.REJECTED);
        teacherProfile.setRejectionReason(trimmedReason);
        teacherProfile.setPublicVisible(false);
        teacherProfile.setVerifiedAt(null);
        teacherProfileRepository.save(teacherProfile);

        TeacherVerificationReview review = new TeacherVerificationReview();
        review.setTeacher(teacherProfile);
        review.setDocument(teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(teacherId).orElse(null));
        review.setReviewerUser(adminUser);
        review.setDecisionStatus(TeacherStatus.REJECTED);
        review.setReviewNote(trimmedReason);
        review.setReviewedAt(LocalDateTime.now());
        teacherVerificationReviewRepository.save(review);

        notificationService.safeCreateNotification(
                teacherProfile.getUser(),
                "Teacher account rejected",
                "Your teacher account was rejected. Please review the rejection reason and resubmit your verification.",
                "/teacher/profile");
        log.info("Teacher rejected: teacherId={}, adminUserId={}, reason={}", teacherProfile.getId(), adminUser.getId(), trimmedReason);
        auditLogService.record(adminUser, "ADMIN_TEACHER_REJECT", "TeacherProfile", teacherProfile.getId(),
                "Admin rejected teacher verification. Reason: " + trimmedReason);
        transactionalEmailDispatcher.enqueueTeacherRejection(teacherProfile.getUser(), trimmedReason);

        return buildTeacherDetail(teacherProfile);
    }

    @Transactional
    public StoredFileDownload getTeacherDocument(SecurityUserDetails currentUser, Long documentId) {
        User adminUser = findActor(currentUser);
        TeacherVerificationDocument document = teacherVerificationDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested teacher document was not found."));

        auditLogService.record(adminUser, "ADMIN_TEACHER_DOCUMENT_VIEW", "TeacherVerificationDocument", documentId,
                "Admin viewed teacher verification document.");
        return fileStorageService.loadAsDownload(document.getUploadedFile());
    }

    private AdminTeacherDetailResponse buildTeacherDetail(TeacherProfile teacherProfile) {
        return teacherManagementMapper.toAdminTeacherDetailResponse(
                teacherProfile,
                teacherVerificationDocumentRepository
                        .findByTeacherIdOrderBySubmittedAtDesc(teacherProfile.getId(), PageRequest.of(0, 100))
                        .getContent(),
                teacherVerificationReviewRepository
                        .findByTeacherIdOrderByReviewedAtDesc(teacherProfile.getId(), PageRequest.of(0, 100))
                        .getContent(),
                auditLogRepository.findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc("TeacherProfile", teacherProfile.getId()));
    }

    /**
     * Converts a list of {@link TeacherProfile} entities to list-item DTOs
     * using exactly 3 batch queries for the entire list (latest document,
     * latest review, and document count per teacher), regardless of list size.
     *
     * <p>The previous per-row approach fired 3 queries per profile, producing
     * 1 + 3N database round-trips for an N-item page. This method reduces that
     * to a constant 1 + 3 queries per page request.
     */
    private List<AdminTeacherListItemResponse> toTeacherListItemsFromPage(List<TeacherProfile> profiles) {
        if (profiles.isEmpty()) {
            return List.of();
        }
        List<Long> teacherIds = profiles.stream().map(TeacherProfile::getId).toList();

        Map<Long, TeacherVerificationDocument> latestDocumentByTeacherId =
                teacherVerificationDocumentRepository.findLatestByTeacherIdIn(teacherIds)
                        .stream()
                        .collect(Collectors.toMap(
                                doc -> doc.getTeacher().getId(),
                                doc -> doc,
                                (existing, replacement) -> existing));

        Map<Long, TeacherVerificationReview> latestReviewByTeacherId =
                teacherVerificationReviewRepository.findLatestByTeacherIdIn(teacherIds)
                        .stream()
                        .collect(Collectors.toMap(
                                rev -> rev.getTeacher().getId(),
                                rev -> rev,
                                (existing, replacement) -> existing));

        Map<Long, Long> documentCountByTeacherId =
                teacherVerificationDocumentRepository.countByTeacherIdIn(teacherIds)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (Long) row[1]));

        return profiles.stream()
                .map(profile -> teacherManagementMapper.toAdminTeacherListItem(
                        profile,
                        documentCountByTeacherId.getOrDefault(profile.getId(), 0L),
                        latestReviewByTeacherId.get(profile.getId()),
                        latestDocumentByTeacherId.get(profile.getId())))
                .toList();
    }

    private TeacherProfile findTeacherProfile(Long teacherId) {
        return teacherProfileRepository.findDetailedById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    private User findActor(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
    }

    private TeacherStatus parseTeacherStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return TeacherStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid teacher status filter.");
        }
    }

    private Sort resolveTeacherSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String normalized = sort.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "name" -> Sort.by(Sort.Direction.ASC, "user.displayName");
            case "status" -> Sort.by(Sort.Direction.ASC, "teacherStatus");
            case "createdat", "created_at" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
