package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationStatusResponse;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;
import com.example.aichinese.mapper.TeacherManagementMapper;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class TeacherManagementService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository teacherVerificationDocumentRepository;
    private final TeacherVerificationReviewRepository teacherVerificationReviewRepository;
    private final UserRepository userRepository;
    private final TeacherManagementMapper teacherManagementMapper;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public TeacherManagementService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository teacherVerificationDocumentRepository,
            TeacherVerificationReviewRepository teacherVerificationReviewRepository,
            UserRepository userRepository,
            TeacherManagementMapper teacherManagementMapper,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherVerificationDocumentRepository = teacherVerificationDocumentRepository;
        this.teacherVerificationReviewRepository = teacherVerificationReviewRepository;
        this.userRepository = userRepository;
        this.teacherManagementMapper = teacherManagementMapper;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getCurrentTeacherProfile(SecurityUserDetails currentUser) {
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());
        List<TeacherVerificationDocument> documents = teacherVerificationDocumentRepository
                .findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId());
        return teacherManagementMapper.toTeacherProfileResponse(profile, documents);
    }

    @Transactional
    public TeacherProfileResponse updateCurrentTeacherProfile(
            SecurityUserDetails currentUser,
            UpdateTeacherProfileRequest request) {
        User user = getUserById(currentUser.getUserId());
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());

        user.setDisplayName(request.fullName().trim());
        profile.setBio(trimToNull(request.bio()));
        profile.setQualificationSummary(trimToNull(request.shortIntroduction()));
        profile.setTeachingCategories(teacherManagementMapper.joinCategories(request.teachingCategories()));

        userRepository.save(user);
        teacherProfileRepository.save(profile);

        List<TeacherVerificationDocument> documents = teacherVerificationDocumentRepository
                .findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId());
        return teacherManagementMapper.toTeacherProfileResponse(profile, documents);
    }

    @Transactional(readOnly = true)
    public TeacherVerificationStatusResponse getTeacherVerificationStatus(SecurityUserDetails currentUser) {
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());
        List<TeacherVerificationDocument> documents = teacherVerificationDocumentRepository
                .findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId());
        TeacherVerificationReview latestReview = teacherVerificationReviewRepository
                .findTopByTeacherIdOrderByReviewedAtDesc(profile.getId())
                .orElse(null);

        return new TeacherVerificationStatusResponse(
                profile.getTeacherStatus(),
                documents.stream().map(teacherManagementMapper::toDocumentSummary).toList(),
                latestReview != null ? teacherManagementMapper.toReviewSummary(latestReview) : null,
                profile.getRejectionReason(),
                profile.getTeacherStatus() == TeacherStatus.REJECTED);
    }

    @Transactional
    public TeacherVerificationStatusResponse uploadTeacherVerificationDocument(
            SecurityUserDetails currentUser,
            MultipartFile file,
            String documentType,
            String description) {
        User user = getUserById(currentUser.getUserId());
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());
        String normalizedDocumentType = TeacherVerificationDocumentType.fromValue(documentType).name();

        UploadedFile uploadedFile = fileStorageService.storeVerificationDocument(file, user);

        teacherVerificationDocumentRepository.findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId())
                .stream()
                .filter(existing -> normalizedDocumentType.equals(existing.getDocumentType()))
                .forEach(existing -> existing.setCurrentVersion(false));

        TeacherVerificationDocument document = new TeacherVerificationDocument();
        document.setTeacher(profile);
        document.setUploadedFile(uploadedFile);
        document.setDocumentType(normalizedDocumentType);
        document.setDocumentNote(trimToNull(description));
        document.setSubmittedAt(LocalDateTime.now());
        document.setCurrentVersion(true);
        teacherVerificationDocumentRepository.save(document);

        notificationAdmins("Teacher verification document uploaded",
                user.getDisplayName() + " uploaded a teacher verification document.",
                "/admin/teacher-approval");
        auditLogService.record(user, "TEACHER_VERIFICATION_DOCUMENT_UPLOAD", "TeacherVerificationDocument", document.getId(),
                "Teacher uploaded a verification document.");

        return getTeacherVerificationStatus(currentUser);
    }

    @Transactional
    public TeacherVerificationStatusResponse resubmitVerification(SecurityUserDetails currentUser) {
        User user = getUserById(currentUser.getUserId());
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());

        if (profile.getTeacherStatus() != TeacherStatus.REJECTED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only rejected teachers can resubmit verification.");
        }

        if (teacherVerificationDocumentRepository.countByTeacherId(profile.getId()) == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Please upload at least one verification document before resubmitting.");
        }

        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setRejectionReason(null);
        profile.setPublicVisible(false);
        teacherProfileRepository.save(profile);

        notificationAdmins(
                "Teacher verification resubmitted",
                user.getDisplayName() + " resubmitted teacher verification for review.",
                "/admin/teacher-approval");
        notificationService.createNotification(
                user,
                "Verification resubmitted",
                "Your teacher verification has been resubmitted and is now pending admin review.",
                "/teacher/verification-status");
        auditLogService.record(user, "TEACHER_VERIFICATION_RESUBMIT", "TeacherProfile", profile.getId(),
                "Teacher resubmitted verification.");

        return getTeacherVerificationStatus(currentUser);
    }

    @Transactional
    public Resource getTeacherOwnedDocument(SecurityUserDetails currentUser, Long documentId) {
        TeacherVerificationDocument document = teacherVerificationDocumentRepository
                .findByIdAndTeacherUserId(documentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested verification document was not found."));

        auditLogService.record(getUserById(currentUser.getUserId()), "TEACHER_VERIFICATION_DOCUMENT_VIEW",
                "TeacherVerificationDocument", documentId, "Teacher viewed their verification document.");
        return fileStorageService.loadAsResource(document.getUploadedFile());
    }

    @Transactional(readOnly = true)
    public TeacherProfile getTeacherProfileEntity(Long teacherId) {
        return teacherProfileRepository.findDetailedById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    @Transactional(readOnly = true)
    public List<TeacherVerificationDocument> getTeacherDocuments(Long teacherId) {
        return teacherVerificationDocumentRepository.findByTeacherIdOrderBySubmittedAtDesc(teacherId);
    }

    @Transactional(readOnly = true)
    public List<TeacherVerificationReview> getTeacherReviews(Long teacherId) {
        return teacherVerificationReviewRepository.findByTeacherIdOrderByReviewedAtDesc(teacherId);
    }

    private TeacherProfile getTeacherProfileByUserId(Long userId) {
        return teacherProfileRepository.findDetailedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void notificationAdmins(String title, String message, String targetPath) {
        List<User> adminUsers = userRepository.findAllByRoleName(RoleName.ADMIN);
        for (User admin : adminUsers) {
            notificationService.createNotification(admin, title, message, targetPath);
        }
    }
}
