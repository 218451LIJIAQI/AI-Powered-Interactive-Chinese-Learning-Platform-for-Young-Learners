package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;

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
import com.example.aichinese.dto.communication.CreateModerationActionRequest;
import com.example.aichinese.dto.communication.ModerationRecordResponse;
import com.example.aichinese.entity.ContentModerationRecord;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentModerationAction;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.ContentModerationRecordRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;

@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);

    private static final int MAX_MODERATION_NOTE_LENGTH = 1000;
    private static final String TEACHER_FEEDBACK_PATH = "/teacher/feedback";

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
        this.contentModerationRecordRepository = Objects.requireNonNull(contentModerationRecordRepository, "contentModerationRecordRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.communicationTargetResolver = Objects.requireNonNull(communicationTargetResolver, "communicationTargetResolver");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional(readOnly = true)
    public PageResponse<ModerationRecordResponse> getAdminModerationRecords(int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<ContentModerationRecord> moderationPage = contentModerationRecordRepository.findAll(newestPageable(safePage, safeSize));
        return new PageResponse<>(
                moderationPage.getContent().stream()
                        .map(communicationMapper::toModerationRecordResponse)
                        .toList(),
                safePage,
                safeSize,
                moderationPage.getTotalElements(),
                PaginationUtils.totalPages(moderationPage.getTotalElements(), safeSize));
    }

    @Transactional(readOnly = true)
    public ModerationRecordResponse getAdminModerationRecord(Long id) {
        ContentModerationRecord record = contentModerationRecordRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Moderation record was not found."));
        return communicationMapper.toModerationRecordResponse(record);
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
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Moderation action request is required.");
        }
        User admin = requireUser(currentUser.getUserId());
        CommunicationTargetResolver.ResolvedTarget target = communicationTargetResolver.resolveModerationTarget(
                request.targetType(),
                request.targetId());

        if (target.lesson() != null) {
            requireValidContentTransition(target.lesson().getContentStatus(), action, "lesson");
            updateLessonStatus(target.lesson(), action);
        } else if (target.coursePackage() != null) {
            requireValidContentTransition(target.coursePackage().getContentStatus(), action, "package");
            updatePackageStatus(target.coursePackage(), action);
        } else if (target.teacherProfile() != null) {
            requireValidVisibilityTransition(target.teacherProfile().isPublicVisible(), action, "teacher profile");
            updateTeacherProfileVisibility(target.teacherProfile(), action);
        }

        ContentModerationRecord saved = saveModerationRecord(admin, target, action, request.reason());
        log.info("Moderation action applied: action={}, targetType={}, targetId={}, adminUserId={}",
                action, target.targetType(), target.targetId(), admin.getId());

        notifyTeacher(target, action, admin);
        auditModerationAction(admin, target, action);

        return communicationMapper.toModerationRecordResponse(saved);
    }

    private ContentModerationRecord saveModerationRecord(
            User admin,
            CommunicationTargetResolver.ResolvedTarget target,
            ContentModerationAction action,
            String reason) {
        ContentModerationRecord record = new ContentModerationRecord();
        record.setAdminUser(admin);
        record.setTargetType(target.targetType());
        record.setTargetId(target.targetId());
        record.setTargetTitleSnapshot(target.targetTitle());
        record.setActionTaken(action.name());
        record.setNote(requireModerationNote(reason));
        record.setRelatedTeacher(target.relatedTeacher());
        return contentModerationRecordRepository.save(record);
    }

    private void notifyTeacher(
            CommunicationTargetResolver.ResolvedTarget target,
            ContentModerationAction action,
            User admin) {
        if (target.relatedTeacher() == null || target.relatedTeacher().getUser() == null) {
            return;
        }
        notificationService.safeCreateNotification(
                target.relatedTeacher().getUser(),
                NotificationType.MODERATION_ACTION,
                "Content moderation update",
                "An admin has applied a moderation action: " + action.name() + ".",
                target.targetType(),
                target.targetId(),
                TEACHER_FEEDBACK_PATH,
                admin);
    }

    private void auditModerationAction(
            User admin,
            CommunicationTargetResolver.ResolvedTarget target,
            ContentModerationAction action) {
        auditLogService.safeRecord(
                admin,
                "MODERATION_" + action.name(),
                target.targetType(),
                target.targetId(),
                "Admin applied moderation action " + action.name()
                        + " to " + target.targetType() + " #" + target.targetId() + ".",
                null);
    }

    private void updateLessonStatus(Lesson lesson, ContentModerationAction action) {
        lesson.setContentStatus(mapContentStatus(action, lesson.getContentStatus()));
    }

    private void updatePackageStatus(CoursePackage coursePackage, ContentModerationAction action) {
        coursePackage.setContentStatus(mapContentStatus(action, coursePackage.getContentStatus()));
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

    private static ContentStatus mapContentStatus(ContentModerationAction action, ContentStatus currentStatus) {
        return switch (action) {
            case HIDE, UNPUBLISH -> ContentStatus.APPROVED;
            case ARCHIVE -> ContentStatus.ARCHIVED;
            case REQUEST_REVISION -> ContentStatus.REJECTED;
            case RESTORE -> currentStatus == ContentStatus.ARCHIVED ? ContentStatus.APPROVED : ContentStatus.PUBLISHED;
        };
    }

    private static void requireValidContentTransition(
            ContentStatus current,
            ContentModerationAction action,
            String entityName) {
        switch (action) {
            case HIDE, UNPUBLISH -> {
                if (current != ContentStatus.PUBLISHED) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "Only published " + entityName + "s can be " + actionLabel(action) + ".");
                }
            }
            case ARCHIVE -> {
                if (current == ContentStatus.ARCHIVED) {
                    throw new ResponseStatusException(BAD_REQUEST, "This " + entityName + " is already archived.");
                }
            }
            case REQUEST_REVISION -> {
                if (current != ContentStatus.PUBLISHED && current != ContentStatus.APPROVED) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "Only published or approved " + entityName + "s can have a revision requested.");
                }
            }
            case RESTORE -> {
                if (current != ContentStatus.ARCHIVED) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "Only archived " + entityName + "s can be restored.");
                }
            }
        }
    }

    private static String actionLabel(ContentModerationAction action) {
        return action.name().toLowerCase().replace('_', '-');
    }

    private static void requireValidVisibilityTransition(
            boolean currentlyVisible,
            ContentModerationAction action,
            String entityName) {
        if (action.isRestrictive() && !currentlyVisible) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This " + entityName + " is already hidden.");
        }
        if (action.isRestorative() && currentlyVisible) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This " + entityName + " is already visible.");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private static String requireModerationNote(String value) {
        String sanitized = AiInputSanitizer.sanitize(value, MAX_MODERATION_NOTE_LENGTH);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Reason is required.");
        }
        return sanitized;
    }

    private static Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
