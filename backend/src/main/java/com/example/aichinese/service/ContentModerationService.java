package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.communication.CreateModerationActionRequest;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentModerationAction;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ContentModerationRecordRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class ContentModerationService {

    private final ContentModerationRecordRepository contentModerationRecordRepository;
    private final UserRepository userRepository;
    private final CommunicationTargetResolver communicationTargetResolver;
    private final CommunicationMapper communicationMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ContentModerationService(
            ContentModerationRecordRepository contentModerationRecordRepository,
            UserRepository userRepository,
            CommunicationTargetResolver communicationTargetResolver,
            CommunicationMapper communicationMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.contentModerationRecordRepository = contentModerationRecordRepository;
        this.userRepository = userRepository;
        this.communicationTargetResolver = communicationTargetResolver;
        this.communicationMapper = communicationMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ModerationRecordResponse> getAdminModerationRecords() {
        // Sort at the DB layer rather than materialising the full list and
        // sorting in JVM memory afterwards.
        return contentModerationRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(communicationMapper::toModerationRecordResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ModerationRecordResponse getAdminModerationRecord(Long id) {
        ContentModerationRecord record = contentModerationRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Moderation record was not found."));
        return communicationMapper.toModerationRecordResponse(record);
    }

    @Transactional(readOnly = true)
    public List<ModerationRecordResponse> getTeacherModerationRecords(SecurityUserDetails currentUser) {
        return contentModerationRecordRepository.findByRelatedTeacherUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .map(communicationMapper::toModerationRecordResponse)
                .toList();
    }

    @Transactional
    public ModerationRecordResponse hide(SecurityUserDetails currentUser, CreateModerationActionRequest request) {
        return applyAction(currentUser, request, ContentModerationAction.HIDE);
    }

    @Transactional
    public ModerationRecordResponse unpublish(SecurityUserDetails currentUser, CreateModerationActionRequest request) {
        return applyAction(currentUser, request, ContentModerationAction.UNPUBLISH);
    }

    @Transactional
    public ModerationRecordResponse archive(SecurityUserDetails currentUser, CreateModerationActionRequest request) {
        return applyAction(currentUser, request, ContentModerationAction.ARCHIVE);
    }

    @Transactional
    public ModerationRecordResponse requestRevision(SecurityUserDetails currentUser, CreateModerationActionRequest request) {
        return applyAction(currentUser, request, ContentModerationAction.REQUEST_REVISION);
    }

    @Transactional
    public ModerationRecordResponse restore(SecurityUserDetails currentUser, CreateModerationActionRequest request) {
        return applyAction(currentUser, request, ContentModerationAction.RESTORE);
    }

    private ModerationRecordResponse applyAction(
            SecurityUserDetails currentUser,
            CreateModerationActionRequest request,
            ContentModerationAction action) {
        User admin = requireUser(currentUser.getUserId());
        CommunicationTargetResolver.ResolvedTarget target = communicationTargetResolver.resolveModerationTarget(
                request.targetType(),
                request.targetId());

        if (target.lesson() != null) {
            updateLessonStatus(target.lesson(), action);
        } else if (target.coursePackage() != null) {
            updatePackageStatus(target.coursePackage(), action);
        } else if (target.review() != null) {
            updateReviewStatus(target.review(), action);
        } else if (target.teacherProfile() != null) {
            updateTeacherProfileVisibility(target.teacherProfile(), action);
        }

        ContentModerationRecord record = new ContentModerationRecord();
        record.setAdminUser(admin);
        record.setTargetType(target.targetType());
        record.setTargetId(target.targetId());
        record.setTargetTitleSnapshot(target.targetTitle());
        record.setActionTaken(action.name());
        record.setNote(request.reason().trim());
        record.setRelatedTeacher(target.relatedTeacher());
        ContentModerationRecord saved = contentModerationRecordRepository.save(record);

        if (target.relatedTeacher() != null && target.relatedTeacher().getUser() != null) {
            notificationService.safeCreateNotification(
                    target.relatedTeacher().getUser(),
                    NotificationType.MODERATION_ACTION,
                    "Content moderation update",
                    "An admin has applied a moderation action: " + action.name() + ".",
                    target.targetType(),
                    target.targetId(),
                    "/teacher/moderation-records",
                    admin);
        }

        auditLogService.safeRecord(
                admin,
                "MODERATION_" + action.name(),
                target.targetType(),
                target.targetId(),
                "Admin applied moderation action " + action.name() + " to " + target.targetType() + " #" + target.targetId() + ".",
                null);

        return communicationMapper.toModerationRecordResponse(saved);
    }

    private void updateLessonStatus(Lesson lesson, ContentModerationAction action) {
        lesson.setContentStatus(mapContentStatus(action, lesson.getContentStatus()));
    }

    private void updatePackageStatus(CoursePackage coursePackage, ContentModerationAction action) {
        coursePackage.setContentStatus(mapContentStatus(action, coursePackage.getContentStatus()));
    }

    private void updateReviewStatus(Review review, ContentModerationAction action) {
        switch (action) {
            case HIDE, UNPUBLISH, ARCHIVE, REQUEST_REVISION -> {
                review.setVisible(false);
                review.setReviewStatus(com.example.aichinese.entity.enums.ReviewStatus.HIDDEN);
            }
            case RESTORE -> {
                review.setVisible(true);
                review.setReviewStatus(com.example.aichinese.entity.enums.ReviewStatus.VISIBLE);
            }
        }
    }

    private void updateTeacherProfileVisibility(TeacherProfile teacherProfile, ContentModerationAction action) {
        switch (action) {
            case HIDE, UNPUBLISH, ARCHIVE -> teacherProfile.setPublicVisible(false);
            case RESTORE -> teacherProfile.setPublicVisible(true);
            case REQUEST_REVISION -> {
                // Keep visibility unchanged while recording the revision request.
            }
        }
    }

    private ContentStatus mapContentStatus(ContentModerationAction action, ContentStatus currentStatus) {
        return switch (action) {
            case HIDE, UNPUBLISH -> ContentStatus.APPROVED;
            case ARCHIVE -> ContentStatus.ARCHIVED;
            case REQUEST_REVISION -> ContentStatus.REJECTED;
            case RESTORE -> currentStatus == ContentStatus.ARCHIVED ? ContentStatus.APPROVED : ContentStatus.PUBLISHED;
        };
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }
}
