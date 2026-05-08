package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Locale;

import org.springframework.core.io.Resource;
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
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

import jakarta.persistence.criteria.Predicate;

@Service
public class AdminTeacherManagementService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository teacherVerificationDocumentRepository;
    private final TeacherVerificationReviewRepository teacherVerificationReviewRepository;
    private final UserRepository userRepository;
    private final TeacherManagementMapper teacherManagementMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
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
            EmailService emailService,
            FileStorageService fileStorageService,
            AuditLogRepository auditLogRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherVerificationDocumentRepository = teacherVerificationDocumentRepository;
        this.teacherVerificationReviewRepository = teacherVerificationReviewRepository;
        this.userRepository = userRepository;
        this.teacherManagementMapper = teacherManagementMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTeacherListItemResponse> listTeachers(
            String status,
            String keyword,
            int page,
            int size,
            String sort) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                resolveTeacherSort(sort));

        TeacherStatus parsedStatus = parseTeacherStatus(status);
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        Page<TeacherProfile> teachers = teacherProfileRepository.findAll((root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();

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
        List<AdminTeacherListItemResponse> items = teachers.getContent().stream()
                .map(this::toTeacherListItem)
                .toList();

        return new PageResponse<>(items, teachers.getNumber(), teachers.getSize(), teachers.getTotalElements(), teachers.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminTeacherDetailResponse getTeacherDetail(Long teacherId) {
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);
        return buildTeacherDetail(teacherProfile);
    }

    @Transactional(readOnly = true)
    public List<AdminTeacherListItemResponse> listPendingTeachers() {
        return teacherProfileRepository.findByTeacherStatusOrderByCreatedAtDesc(TeacherStatus.PENDING).stream()
                .map(this::toTeacherListItem)
                .toList();
    }

    @Transactional
    public AdminTeacherDetailResponse approveTeacher(SecurityUserDetails currentUser, Long teacherId, String note) {
        User adminUser = findActor(currentUser);
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);

        teacherProfile.setTeacherStatus(TeacherStatus.APPROVED);
        teacherProfile.setRejectionReason(null);
        teacherProfile.setPublicVisible(true);
        teacherProfile.setVerifiedAt(java.time.LocalDateTime.now());
        teacherProfileRepository.save(teacherProfile);

        TeacherVerificationReview review = new TeacherVerificationReview();
        review.setTeacher(teacherProfile);
        review.setDocument(teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(teacherId).orElse(null));
        review.setReviewerUser(adminUser);
        review.setDecisionStatus(TeacherStatus.APPROVED);
        review.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        review.setReviewedAt(java.time.LocalDateTime.now());
        teacherVerificationReviewRepository.save(review);

        notificationService.createNotification(
                teacherProfile.getUser(),
                "Teacher account approved",
                "Your teacher account has been approved by an administrator.",
                "/teacher/verification-status");
        auditLogService.record(adminUser, "ADMIN_TEACHER_APPROVE", "TeacherProfile", teacherProfile.getId(),
                "Admin approved teacher verification.");
        emailService.sendTeacherApprovalNotice(teacherProfile.getUser(), note);

        return buildTeacherDetail(teacherProfile);
    }

    @Transactional
    public AdminTeacherDetailResponse rejectTeacher(SecurityUserDetails currentUser, Long teacherId, String reason) {
        User adminUser = findActor(currentUser);
        TeacherProfile teacherProfile = findTeacherProfile(teacherId);

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Rejection reason is required.");
        }

        teacherProfile.setTeacherStatus(TeacherStatus.REJECTED);
        teacherProfile.setRejectionReason(reason.trim());
        teacherProfile.setPublicVisible(false);
        teacherProfile.setVerifiedAt(null);
        teacherProfileRepository.save(teacherProfile);

        TeacherVerificationReview review = new TeacherVerificationReview();
        review.setTeacher(teacherProfile);
        review.setDocument(teacherVerificationDocumentRepository.findTopByTeacherIdOrderBySubmittedAtDesc(teacherId).orElse(null));
        review.setReviewerUser(adminUser);
        review.setDecisionStatus(TeacherStatus.REJECTED);
        review.setReviewNote(reason.trim());
        review.setReviewedAt(java.time.LocalDateTime.now());
        teacherVerificationReviewRepository.save(review);

        notificationService.createNotification(
                teacherProfile.getUser(),
                "Teacher account rejected",
                "Your teacher account was rejected. Please review the rejection reason and resubmit your verification.",
                "/teacher/verification-status");
        auditLogService.record(adminUser, "ADMIN_TEACHER_REJECT", "TeacherProfile", teacherProfile.getId(),
                "Admin rejected teacher verification.");
        emailService.sendTeacherRejectionNotice(teacherProfile.getUser(), reason.trim());

        return buildTeacherDetail(teacherProfile);
    }

    @Transactional
    public Resource getTeacherDocument(SecurityUserDetails currentUser, Long documentId) {
        User adminUser = findActor(currentUser);
        TeacherVerificationDocument document = teacherVerificationDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested teacher document was not found."));

        auditLogService.record(adminUser, "ADMIN_TEACHER_DOCUMENT_VIEW", "TeacherVerificationDocument", documentId,
                "Admin viewed teacher verification document.");
        return fileStorageService.loadAsResource(document.getUploadedFile());
    }

    private AdminTeacherDetailResponse buildTeacherDetail(TeacherProfile teacherProfile) {
        return teacherManagementMapper.toAdminTeacherDetailResponse(
                teacherProfile,
                teacherVerificationDocumentRepository.findByTeacherIdOrderBySubmittedAtDesc(teacherProfile.getId()),
                teacherVerificationReviewRepository.findByTeacherIdOrderByReviewedAtDesc(teacherProfile.getId()),
                auditLogRepository.findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc("TeacherProfile", teacherProfile.getId()));
    }

    private AdminTeacherListItemResponse toTeacherListItem(TeacherProfile profile) {
        TeacherVerificationReview latestReview = teacherVerificationReviewRepository
                .findTopByTeacherIdOrderByReviewedAtDesc(profile.getId())
                .orElse(null);
        TeacherVerificationDocument latestDocument = teacherVerificationDocumentRepository
                .findTopByTeacherIdOrderBySubmittedAtDesc(profile.getId())
                .orElse(null);
        long documentCount = teacherVerificationDocumentRepository.countByTeacherId(profile.getId());
        return teacherManagementMapper.toAdminTeacherListItem(profile, documentCount, latestReview, latestDocument);
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
            return TeacherStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid teacher status filter.");
        }
    }

    private Sort resolveTeacherSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "name" -> Sort.by(Sort.Direction.ASC, "user.displayName");
            case "status" -> Sort.by(Sort.Direction.ASC, "teacherStatus");
            case "createdat", "created_at" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
