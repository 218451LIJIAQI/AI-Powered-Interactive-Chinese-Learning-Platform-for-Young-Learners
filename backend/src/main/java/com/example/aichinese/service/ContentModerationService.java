package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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
import com.example.aichinese.util.PaginationUtils;

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
    public PageResponse<ModerationRecordResponse> getAdminModerationRecords(int page, int size) {
        // Sort at the DB layer rather than materialising the full list and
        // sorting in JVM memory afterwards.
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
        User admin = requireUser(currentUser.getUserId());
        CommunicationTargetResolver.ResolvedTarget target = communicationTargetResolver.resolveModerationTarget(
                request.targetType(),
                request.targetId());

        // Wave-11 state-machine validation. The previous {@link #mapContentStatus}
        // implementation silently mapped the action to a target status
        // regardless of the source state, which let an admin click HIDE on
        // an already-ARCHIVED lesson and quietly send it back to APPROVED
        // (a state regression), or click HIDE on an APPROVED-but-not-yet-
        // published lesson and silently re-set it to APPROVED (a no-op
        // that still produced a moderation record + audit row + teacher
        // notification). Validating the source state up front converts
        // these cases into a clear 400 instead of corrupting the audit
        // trail.
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
                    "/teacher/feedback",
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

    /**
     * Conservative state-machine validator for lesson and package targets.
     * Refuses moderation actions whose source state would be nonsensical or
     * regressive:
     *
     * <ul>
     *   <li>{@code HIDE} / {@code UNPUBLISH}: only valid on PUBLISHED content.
     *       Applying them to APPROVED, ARCHIVED, or REJECTED content would
     *       either be a no-op or roll the state backwards.</li>
     *   <li>{@code ARCHIVE}: valid from any non-ARCHIVED state. The double-
     *       archive case is rejected here for parity with
     *       {@code AdminContentReviewService.archiveContent}.</li>
     *   <li>{@code REQUEST_REVISION}: valid only on content that is currently
     *       reachable by students or pending publication, i.e. PUBLISHED or
     *       APPROVED. Asking a teacher to revise a DRAFT or REJECTED item
     *       does not make sense (it is already not public).</li>
     *   <li>{@code RESTORE}: valid only when content is currently ARCHIVED.
     *       Restoring a PUBLISHED or APPROVED item is a state no-op.</li>
     * </ul>
     */
    private void requireValidContentTransition(
            ContentStatus current,
            ContentModerationAction action,
            String entityName) {
        switch (action) {
            case HIDE, UNPUBLISH -> {
                if (current != ContentStatus.PUBLISHED) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "Only published " + entityName + "s can be " + action.name().toLowerCase().replace('_', '-') + ".");
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

    /**
     * Visibility-flag analogue of {@link #requireValidContentTransition} for
     * teacher-profile targets, which use a boolean visible flag
     * rather than a {@link ContentStatus}. HIDE/UNPUBLISH/ARCHIVE/
     * REQUEST_REVISION require the target to be currently visible; RESTORE
     * requires it to be currently hidden. Without these guards, an admin
     * clicking the same moderation button twice would write a duplicate
     * moderation record + duplicate audit row + duplicate teacher
     * notification for a state-no-op event.
     */
    private void requireValidVisibilityTransition(
            boolean currentlyVisible,
            ContentModerationAction action,
            String entityName) {
        switch (action) {
            case HIDE, UNPUBLISH, ARCHIVE, REQUEST_REVISION -> {
                if (!currentlyVisible) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "This " + entityName + " is already hidden.");
                }
            }
            case RESTORE -> {
                if (currentlyVisible) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "This " + entityName + " is already visible.");
                }
            }
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User could not be found."));
    }

    private Pageable newestPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
