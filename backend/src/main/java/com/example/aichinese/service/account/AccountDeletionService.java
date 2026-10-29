package com.example.aichinese.service.account;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.SupportMessage;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.EmailChangeTokenRepository;
import com.example.aichinese.repository.EmailConfirmationTokenRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.PasswordResetTokenRepository;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.TeacherVerificationReviewRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.AuditLogService.AuditTarget;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.SecureTokens;

/**
 * A student or teacher deletes their own account. The user row stays, because learning records, reviews and audit
 * entries point to it, but it is anonymised and can never sign in again; the personal data the account owns is
 * removed: name and e-mail, notifications, support requests with their screenshots, pending e-mail changes and
 * confirmation links, the learning goal in the student's own words (and what SparkBot wrote from it), and a
 * teacher's bio and verification documents. Admin accounts are deactivated by another admin instead, and a teacher
 * archives their published classes first.
 */
@Service
public class AccountDeletionService {

    private static final String DELETED_NAME = "Deleted account";

    private final UserRepository userRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository documentRepository;
    private final TeacherVerificationReviewRepository reviewRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final NotificationRepository notificationRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;
    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final FileStorageService fileStorageService;
    private final RefreshTokenService refreshTokenService;
    private final PublishedClassLookup publishedClassLookup;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AccountDeletionService(
            UserRepository userRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository documentRepository,
            TeacherVerificationReviewRepository reviewRepository,
            SupportRequestRepository supportRequestRepository,
            SupportMessageRepository supportMessageRepository,
            NotificationRepository notificationRepository,
            EmailChangeTokenRepository emailChangeTokenRepository,
            EmailConfirmationTokenRepository emailConfirmationTokenRepository,
            LearningPlanRepository learningPlanRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            FileStorageService fileStorageService,
            RefreshTokenService refreshTokenService,
            PublishedClassLookup publishedClassLookup,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.supportRequestRepository = supportRequestRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.notificationRepository = notificationRepository;
        this.emailChangeTokenRepository = emailChangeTokenRepository;
        this.emailConfirmationTokenRepository = emailConfirmationTokenRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.fileStorageService = fileStorageService;
        this.refreshTokenService = refreshTokenService;
        this.publishedClassLookup = publishedClassLookup;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void delete(SecurityUserDetails currentUser, String confirmEmail) {
        User user = userRepository.findWithRolesById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user could not be found."));
        if (!user.getEmail().equals(confirmEmail.strip().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(BAD_REQUEST, "The email address you typed doesn't match your account.");
        }
        RoleName role = AccountRoles.primary(user);
        if (role == RoleName.ADMIN) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Admin accounts can't be deleted. Another admin can deactivate this account.");
        }
        if (role == RoleName.TEACHER && !publishedClassLookup.publishedClasses(user).isEmpty()) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Archive your published classes before you delete your account.");
        }

        Long userId = user.getId();
        removeSupportRequests(userId);
        notificationRepository.deleteByUserId(userId);
        emailChangeTokenRepository.deleteByUserId(userId);
        emailConfirmationTokenRepository.deleteByUserId(userId);
        learningPlanRepository.clearOwnWords(userId);
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.invalidateActiveTokensForUser(userId, now);
        teacherProfileRepository.findByUserId(userId).ifPresent(this::removeTeachingProfile);

        // Reloaded: the bulk deletes above cleared the persistence context.
        User account = userRepository.findById(userId).orElseThrow();
        account.setEmail("deleted-" + userId + "@deleted.invalid");
        account.setDisplayName(DELETED_NAME);
        account.setPasswordHash(passwordEncoder.encode(SecureTokens.newRawToken()));
        account.setActive(false);
        account.setDeletedAt(now);
        refreshTokenService.invalidateSessions(account);
        auditLogService.record(account, "ACCOUNT_DELETED", new AuditTarget("User", userId, DELETED_NAME), null,
                "The owner deleted the " + role.name().toLowerCase(Locale.ROOT) + " account; its personal data was removed.");
    }

    private void removeSupportRequests(Long userId) {
        List<SupportRequest> requests = supportRequestRepository.findByRequesterId(userId);
        if (requests.isEmpty()) {
            return;
        }
        List<Long> requestIds = requests.stream().map(SupportRequest::getId).toList();
        List<UploadedFile> screenshots = requestIds.stream()
                .flatMap(requestId -> supportMessageRepository.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream())
                .map(SupportMessage::getAttachment)
                .filter(Objects::nonNull)
                .toList();
        supportMessageRepository.deleteByRequestIdIn(requestIds);
        supportRequestRepository.deleteAllByIdInBatch(requestIds);
        screenshots.forEach(fileStorageService::deleteSupportScreenshot);
    }

    private void removeTeachingProfile(TeacherProfile profile) {
        List<TeacherVerificationDocument> documents = documentRepository.findByTeacherId(profile.getId());
        reviewRepository.detachDocuments(profile.getId());
        List<UploadedFile> files = documents.stream().map(TeacherVerificationDocument::getUploadedFile).toList();
        documentRepository.deleteAllByIdInBatch(documents.stream().map(TeacherVerificationDocument::getId).toList());
        files.forEach(fileStorageService::deleteVerificationDocument);

        TeacherProfile reloaded = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        reloaded.setBio(null);
        reloaded.setQualificationSummary(null);
        reloaded.setTeachingCategories(null);
        teacherProfileRepository.save(reloaded);
    }
}
