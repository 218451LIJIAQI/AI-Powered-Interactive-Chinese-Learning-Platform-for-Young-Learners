package com.example.aichinese.service.teacher;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.TeacherVerificationReview;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.account.PublishedClassLookup;

/**
 * The teacher's side of verification: the public profile, the documents (teaching certificate, identity document,
 * optional degree certificate) and sending the application to the admins. Documents can be replaced only before the
 * first submission and after an admin asked for changes; while an application is in review it is read-only.
 */
@Service
public class TeacherVerificationService {

    public static final String ACTION_APPLICATION_SUBMITTED = "TEACHER_APPLICATION_SUBMITTED";
    static final String ACTION_DOCUMENT_UPLOAD = "TEACHER_VERIFICATION_DOCUMENT_UPLOAD";
    static final String ACTION_DOCUMENT_VIEW = "TEACHER_VERIFICATION_DOCUMENT_VIEW";
    static final String TARGET_TEACHER_PROFILE = "TeacherProfile";
    private static final String TARGET_DOCUMENT = "TeacherVerificationDocument";
    private static final String ADMIN_APPLICATIONS_PATH = "/admin/users/applications";

    /** The documents a teacher can upload; the first two are required before submitting. */
    public static final Set<TeacherVerificationDocumentType> REQUIRED_DOCUMENTS =
            EnumSet.of(TeacherVerificationDocumentType.CERTIFICATE, TeacherVerificationDocumentType.IDENTITY);
    private static final Set<TeacherVerificationDocumentType> ACCEPTED_DOCUMENTS = EnumSet.of(
            TeacherVerificationDocumentType.CERTIFICATE,
            TeacherVerificationDocumentType.IDENTITY,
            TeacherVerificationDocumentType.QUALIFICATION);

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository documentRepository;
    private final TeacherVerificationReviewRepository reviewRepository;
    private final PublishedClassLookup publishedClassLookup;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public TeacherVerificationService(
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository documentRepository,
            TeacherVerificationReviewRepository reviewRepository,
            PublishedClassLookup publishedClassLookup,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.publishedClassLookup = publishedClassLookup;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public TeacherProfileResponse getProfile(SecurityUserDetails currentUser) {
        return toResponse(requireProfile(currentUser));
    }

    @Transactional
    public TeacherProfileResponse updateProfile(SecurityUserDetails currentUser, UpdateTeacherProfileRequest request) {
        TeacherProfile profile = requireProfile(currentUser);
        profile.getUser().setDisplayName(request.fullName());
        profile.setBio(request.bio());
        userRepository.save(profile.getUser());
        teacherProfileRepository.save(profile);
        return toResponse(profile);
    }

    /** Adds or replaces the teacher's current document of one type. */
    @Transactional
    public TeacherProfileResponse uploadDocument(
            SecurityUserDetails currentUser, MultipartFile file, TeacherVerificationDocumentType documentType) {
        if (documentType == null || !ACCEPTED_DOCUMENTS.contains(documentType)) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Upload a teaching certificate, an identity document or a degree certificate.");
        }
        TeacherProfile profile = requireProfile(currentUser);
        requireEditable(TeacherVerificationState.of(profile));
        User user = profile.getUser();

        UploadedFile uploadedFile = fileStorageService.storeVerificationDocument(file, user);
        List<TeacherVerificationDocument> replaced = currentDocuments(profile).stream()
                .filter(existing -> existing.getDocumentType() == documentType)
                .toList();
        if (!replaced.isEmpty()) {
            replaced.forEach(existing -> existing.setCurrentVersion(false));
            // Written before the new row, which is inserted at once (IDENTITY ids): the unique key
            // uk_teacher_verification_documents_current_type allows one current document per type.
            documentRepository.saveAllAndFlush(replaced);
        }

        TeacherVerificationDocument document = new TeacherVerificationDocument();
        document.setTeacher(profile);
        document.setUploadedFile(uploadedFile);
        document.setDocumentType(documentType);
        document.setSubmittedAt(now());
        document.setCurrentVersion(true);
        documentRepository.save(document);

        auditLogService.record(user, ACTION_DOCUMENT_UPLOAD, new AuditTarget(TARGET_DOCUMENT, document.getId(),
                user.getDisplayName()), null, "Teacher uploaded a verification document.");
        return toResponse(profile);
    }

    /**
     * Sends the application to the admins (again, after changes were requested): the teaching certificate and the
     * identity document are required, and the teacher's consent to the teacher expectations is recorded.
     */
    @Transactional
    public TeacherProfileResponse submit(SecurityUserDetails currentUser) {
        TeacherProfile profile = requireProfile(currentUser);
        TeacherVerificationState state = TeacherVerificationState.of(profile);
        if (state == TeacherVerificationState.IN_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "Your application is already in review.");
        }
        requireEditable(state);
        Set<TeacherVerificationDocumentType> uploaded = EnumSet.noneOf(TeacherVerificationDocumentType.class);
        currentDocuments(profile).forEach(document -> uploaded.add(document.getDocumentType()));
        if (!uploaded.containsAll(REQUIRED_DOCUMENTS)) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Upload your teaching certificate and identity document before you submit.");
        }

        LocalDateTime now = now();
        boolean resubmission = state == TeacherVerificationState.CHANGES_REQUESTED;
        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setRejectionReason(null);
        profile.setVerificationSubmittedAt(now);
        profile.setStandardsAcceptedAt(now);
        teacherProfileRepository.save(profile);

        User user = profile.getUser();
        for (User admin : userRepository.findAllByRoleName(RoleName.ADMIN)) {
            if (admin.isActive()) {
                notificationService.safeCreateNotification(
                        admin,
                        NotificationType.TEACHER_APPROVAL,
                        resubmission ? "Teacher application resubmitted" : "New teacher application",
                        user.getDisplayName() + (resubmission
                                ? " sent updated verification documents."
                                : " submitted verification documents."),
                        TARGET_TEACHER_PROFILE,
                        profile.getId(),
                        ADMIN_APPLICATIONS_PATH + "?teacher=" + profile.getId(),
                        user);
            }
        }
        auditLogService.record(user, ACTION_APPLICATION_SUBMITTED,
                new AuditTarget(TARGET_TEACHER_PROFILE, profile.getId(), user.getDisplayName()), null,
                resubmission ? "Teacher resubmitted the application." : "Teacher submitted the application.");
        return toResponse(profile);
    }

    @Transactional
    public StoredFileDownload getOwnDocument(SecurityUserDetails currentUser, Long documentId) {
        TeacherVerificationDocument document = documentRepository
                .findByIdAndTeacherUserId(documentId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested verification document was not found."));
        auditLogService.record(currentUser, ACTION_DOCUMENT_VIEW,
                TARGET_DOCUMENT, documentId, "Teacher viewed their verification document.");
        return fileStorageService.loadVerificationDocument(document.getUploadedFile());
    }

    private TeacherProfileResponse toResponse(TeacherProfile profile) {
        User user = profile.getUser();
        TeacherVerificationState state = TeacherVerificationState.of(profile);
        TeacherVerificationReview latestReview = reviewRepository
                .findTopByTeacherIdOrderByReviewedAtDesc(profile.getId())
                .orElse(null);
        LocalDateTime lastDecisionAt = latestReview == null ? null : latestReview.getReviewedAt();
        List<TeacherProfileResponse.Document> documents = currentDocuments(profile).stream()
                .sorted((left, right) -> left.getDocumentType().compareTo(right.getDocumentType()))
                .map(document -> toDocument(document, state, lastDecisionAt))
                .toList();
        return new TeacherProfileResponse(
                profile.getId(),
                user.getDisplayName(),
                user.getEmail(),
                profile.getBio(),
                state,
                profile.getVerificationSubmittedAt(),
                state == TeacherVerificationState.APPROVED ? profile.getVerifiedAt() : null,
                latestReview == null ? null : toDecision(latestReview),
                documents,
                publishedClassLookup.publishedClasses(user));
    }

    private static TeacherProfileResponse.Document toDocument(
            TeacherVerificationDocument document, TeacherVerificationState state, LocalDateTime lastDecisionAt) {
        UploadedFile file = document.getUploadedFile();
        TeacherProfileResponse.DocumentStatus status = switch (state) {
            case NOT_SUBMITTED -> TeacherProfileResponse.DocumentStatus.READY;
            case IN_REVIEW -> TeacherProfileResponse.DocumentStatus.IN_REVIEW;
            case APPROVED -> TeacherProfileResponse.DocumentStatus.VERIFIED;
            case CHANGES_REQUESTED -> lastDecisionAt != null && document.getSubmittedAt().isAfter(lastDecisionAt)
                    ? TeacherProfileResponse.DocumentStatus.READY
                    : TeacherProfileResponse.DocumentStatus.SENT;
        };
        return new TeacherProfileResponse.Document(
                document.getId(),
                document.getDocumentType(),
                file.getFileName(),
                file.getFileSize(),
                document.getSubmittedAt(),
                status);
    }

    private static TeacherProfileResponse.Decision toDecision(TeacherVerificationReview review) {
        User reviewer = review.getReviewerUser();
        return new TeacherProfileResponse.Decision(
                review.getDecisionStatus() == TeacherStatus.APPROVED
                        ? TeacherVerificationState.APPROVED
                        : TeacherVerificationState.CHANGES_REQUESTED,
                review.getDecisionStatus() == TeacherStatus.APPROVED ? null : review.getReviewNote(),
                reviewer == null ? null : reviewer.getDisplayName(),
                review.getReviewedAt());
    }

    private static void requireEditable(TeacherVerificationState state) {
        if (state == TeacherVerificationState.IN_REVIEW) {
            throw new ResponseStatusException(BAD_REQUEST, "Documents can't be changed while they are in review.");
        }
        if (state == TeacherVerificationState.APPROVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Your documents are already verified.");
        }
    }

    private TeacherProfile requireProfile(SecurityUserDetails currentUser) {
        return teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));
    }

    private List<TeacherVerificationDocument> currentDocuments(TeacherProfile profile) {
        return documentRepository.findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(profile.getId());
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
