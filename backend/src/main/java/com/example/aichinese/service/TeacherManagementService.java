package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.aichinese.util.TextUtils;

@Service
public class TeacherManagementService {

    private static final Logger log = LoggerFactory.getLogger(TeacherManagementService.class);
    private static final String TEACHER_PROFILE_NOT_FOUND = "Teacher profile could not be found.";
    private static final String USER_NOT_FOUND = "User could not be found.";
    private static final String TEACHER_DOCUMENT_NOT_FOUND = "Requested verification document was not found.";
    private static final String TEACHER_PROFILE_TARGET = "TeacherProfile";
    private static final String TEACHER_DOCUMENT_TARGET = "TeacherVerificationDocument";
    private static final String ADMIN_VERIFICATION_PATH = "/admin/teacher-verification";
    private static final String TEACHER_PROFILE_PATH = "/teacher/profile";
    private static final String ACTION_DOCUMENT_UPLOAD = "TEACHER_VERIFICATION_DOCUMENT_UPLOAD";
    private static final String ACTION_DOCUMENT_VIEW = "TEACHER_VERIFICATION_DOCUMENT_VIEW";
    private static final String ACTION_VERIFICATION_RESUBMIT = "TEACHER_VERIFICATION_RESUBMIT";
    /** Length of {@code teacher_profiles.teaching_categories}, which stores the joined category list. */
    private static final int MAX_TEACHING_CATEGORIES_LENGTH = 500;
    private static final String TEACHING_CATEGORIES_TOO_LONG =
            "Teaching categories must not exceed " + MAX_TEACHING_CATEGORIES_LENGTH + " characters in total.";

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
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.teacherVerificationDocumentRepository = Objects.requireNonNull(teacherVerificationDocumentRepository, "teacherVerificationDocumentRepository");
        this.teacherVerificationReviewRepository = Objects.requireNonNull(teacherVerificationReviewRepository, "teacherVerificationReviewRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.teacherManagementMapper = Objects.requireNonNull(teacherManagementMapper, "teacherManagementMapper");
        this.fileStorageService = Objects.requireNonNull(fileStorageService, "fileStorageService");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getCurrentTeacherProfile(SecurityUserDetails currentUser) {
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());
        return teacherManagementMapper.toTeacherProfileResponse(profile, currentDocuments(profile));
    }

    @Transactional
    public TeacherProfileResponse updateCurrentTeacherProfile(
            SecurityUserDetails currentUser,
            UpdateTeacherProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Teacher profile update request is required.");
        }
        String teachingCategories = teacherManagementMapper.joinCategories(request.teachingCategories());
        if (teachingCategories != null && teachingCategories.length() > MAX_TEACHING_CATEGORIES_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, TEACHING_CATEGORIES_TOO_LONG);
        }
        User user = getUserById(currentUser.getUserId());
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());

        String displayName = TextUtils.trimToNull(request.fullName());
        user.setDisplayName(displayName == null ? user.getDisplayName() : displayName);
        profile.setBio(TextUtils.trimToNull(request.bio()));
        profile.setQualificationSummary(TextUtils.trimToNull(request.shortIntroduction()));
        profile.setTeachingCategories(teachingCategories);

        userRepository.save(user);
        teacherProfileRepository.save(profile);
        log.info("Teacher profile updated: teacherId={}, userId={}", profile.getId(), user.getId());

        return teacherManagementMapper.toTeacherProfileResponse(profile, currentDocuments(profile));
    }

    @Transactional(readOnly = true)
    public TeacherVerificationStatusResponse getTeacherVerificationStatus(SecurityUserDetails currentUser) {
        TeacherProfile profile = getTeacherProfileByUserId(currentUser.getUserId());
        TeacherVerificationReview latestReview = teacherVerificationReviewRepository
                .findTopByTeacherIdOrderByReviewedAtDesc(profile.getId())
                .orElse(null);

        return new TeacherVerificationStatusResponse(
                profile.getTeacherStatus(),
                currentDocuments(profile).stream().map(teacherManagementMapper::toDocumentSummary).toList(),
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
        TeacherVerificationDocumentType normalizedDocumentType = TeacherVerificationDocumentType.fromValue(documentType);

        UploadedFile uploadedFile = fileStorageService.storeVerificationDocument(file, user);

        currentDocuments(profile).stream()
                .filter(existing -> normalizedDocumentType == existing.getDocumentType())
                .forEach(existing -> existing.setCurrentVersion(false));

        TeacherVerificationDocument document = new TeacherVerificationDocument();
        document.setTeacher(profile);
        document.setUploadedFile(uploadedFile);
        document.setDocumentType(normalizedDocumentType);
        document.setDocumentNote(TextUtils.trimToNull(description));
        document.setSubmittedAt(LocalDateTime.now());
        document.setCurrentVersion(true);
        teacherVerificationDocumentRepository.save(document);

        notifyAdmins("Teacher verification document uploaded",
                user.getDisplayName() + " uploaded a teacher verification document.",
                ADMIN_VERIFICATION_PATH);
        log.info("Verification document uploaded: docId={}, teacherId={}, type={}",
                document.getId(), profile.getId(), normalizedDocumentType);
        auditLogService.record(user, ACTION_DOCUMENT_UPLOAD, TEACHER_DOCUMENT_TARGET, document.getId(),
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

        notifyAdmins(
                "Teacher verification resubmitted",
                user.getDisplayName() + " resubmitted teacher verification for review.",
                ADMIN_VERIFICATION_PATH);
        notificationService.safeCreateNotification(
                user,
                "Verification resubmitted",
                "Your teacher verification has been resubmitted and is now pending admin review.",
                TEACHER_PROFILE_PATH);
        log.info("Teacher verification resubmitted: teacherId={}, userId={}", profile.getId(), user.getId());
        auditLogService.record(user, ACTION_VERIFICATION_RESUBMIT, TEACHER_PROFILE_TARGET, profile.getId(),
                "Teacher resubmitted verification.");

        return getTeacherVerificationStatus(currentUser);
    }

    @Transactional
    public StoredFileDownload getTeacherOwnedDocument(SecurityUserDetails currentUser, Long documentId) {
        TeacherVerificationDocument document = teacherVerificationDocumentRepository
                .findByIdAndTeacherUserId(documentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, TEACHER_DOCUMENT_NOT_FOUND));

        auditLogService.record(getUserById(currentUser.getUserId()), ACTION_DOCUMENT_VIEW,
                TEACHER_DOCUMENT_TARGET, documentId, "Teacher viewed their verification document.");
        return fileStorageService.loadAsDownload(document.getUploadedFile());
    }

    private TeacherProfile getTeacherProfileByUserId(Long userId) {
        return teacherProfileRepository.findDetailedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, TEACHER_PROFILE_NOT_FOUND));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, USER_NOT_FOUND));
    }

    private void notifyAdmins(String title, String message, String targetPath) {
        List<User> adminUsers = userRepository.findAllByRoleName(RoleName.ADMIN);
        for (User admin : adminUsers) {
            notificationService.safeCreateNotification(admin, title, message, targetPath);
        }
    }

    private List<TeacherVerificationDocument> currentDocuments(TeacherProfile profile) {
        return teacherVerificationDocumentRepository
                .findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId());
    }
}
