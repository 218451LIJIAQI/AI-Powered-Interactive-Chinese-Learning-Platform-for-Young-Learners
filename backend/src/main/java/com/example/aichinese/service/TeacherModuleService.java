package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.CreateModuleRequest;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.UpdateModuleRequest;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Teacher-side CRUD for {@link LearningModule}s the teacher owns.
 *
 * <p>Modules group lessons; this service handles create / update /
 * archive plus the ownership lookups consumed by
 * {@link TeacherLessonService} and {@link TeacherPackageService}.
 * Approved-teacher gating runs through
 * {@link TeacherContentAccessService} so a pending or rejected
 * teacher cannot author or mutate modules.</p>
 */
@Service
public class TeacherModuleService {

    private static final String MODULE_REQUEST_REQUIRED = "Module request is required.";
    private static final String MODULE_NOT_FOUND = "Requested module was not found.";
    private static final String AUDIT_TARGET_MODULE = "LearningModule";
    private static final String ACTION_CREATE = "TEACHER_MODULE_CREATE";
    private static final String ACTION_UPDATE = "TEACHER_MODULE_UPDATE";
    private static final String ACTION_ARCHIVE = "TEACHER_MODULE_ARCHIVE";
    private static final ReviewContentType REVIEW_TYPE = ReviewContentType.MODULE;

    private final LearningModuleRepository learningModuleRepository;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;
    private final ContentReviewService contentReviewService;

    public TeacherModuleService(
            LearningModuleRepository learningModuleRepository,
            TeacherContentAccessService teacherContentAccessService,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService,
            ContentReviewService contentReviewService) {
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.contentReviewService = Objects.requireNonNull(contentReviewService, "contentReviewService");
    }

    @Transactional(readOnly = true)
    public PageResponse<LearningModuleResponse> getTeacherModules(SecurityUserDetails currentUser, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<LearningModule> modulePage = learningModuleRepository.findByTeacherUserIdOrderByUpdatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        List<LearningModuleResponse> items = modulePage.getContent().stream()
                .map(contentManagementMapper::toLearningModuleResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, modulePage.getTotalElements(), modulePage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LearningModuleResponse getTeacherModule(SecurityUserDetails currentUser, Long moduleId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return contentManagementMapper.toLearningModuleResponse(findOwnedModule(currentUser, moduleId));
    }

    @Transactional
    public LearningModuleResponse createModule(SecurityUserDetails currentUser, CreateModuleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, MODULE_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);

        LearningModule module = new LearningModule();
        attachModuleMetadata(module, teacherProfile, ModuleMetadata.from(request));
        // Review workflow (D4): new modules are drafts until an admin approves them.
        module.setContentStatus(ContentStatus.DRAFT);
        module.setRejectionReason(null);

        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                ACTION_CREATE,
                AUDIT_TARGET_MODULE,
                saved.getId(),
                "Teacher created a learning module draft.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional
    public LearningModuleResponse updateModule(SecurityUserDetails currentUser, Long moduleId, UpdateModuleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, MODULE_REQUEST_REQUIRED);
        }
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LearningModule module = findOwnedModule(currentUser, moduleId);
        contentReviewService.requireTeacherEditable(module, REVIEW_TYPE);

        attachModuleMetadata(module, teacherProfile, ModuleMetadata.from(request));
        contentReviewService.recordTeacherEdit(teacherProfile.getUser(), REVIEW_TYPE, module);

        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                ACTION_UPDATE,
                AUDIT_TARGET_MODULE,
                saved.getId(),
                "Teacher updated a learning module.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional
    public LearningModuleResponse submitModule(SecurityUserDetails currentUser, Long moduleId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LearningModule module = findOwnedModule(currentUser, moduleId);
        contentReviewService.submitForReview(teacherProfile.getUser(), REVIEW_TYPE, module);
        return contentManagementMapper.toLearningModuleResponse(learningModuleRepository.save(module));
    }

    @Transactional
    public LearningModuleResponse archiveModule(SecurityUserDetails currentUser, Long moduleId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LearningModule module = findOwnedModule(currentUser, moduleId);
        teacherContentAccessService.validateArchivableStatus(module.getContentStatus(), "Module");

        module.setContentStatus(ContentStatus.ARCHIVED);
        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                ACTION_ARCHIVE,
                AUDIT_TARGET_MODULE,
                saved.getId(),
                "Teacher archived a learning module.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional(readOnly = true)
    public LearningModule findOwnedModule(SecurityUserDetails currentUser, Long moduleId) {
        return learningModuleRepository.findByIdAndTeacherUserId(moduleId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, MODULE_NOT_FOUND));
    }

    private void attachModuleMetadata(
            LearningModule module,
            TeacherProfile teacherProfile,
            ModuleMetadata request) {
        teacherContentAccessService.attachModuleMetadata(
                module,
                teacherProfile,
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.coverImageUrl());
    }

    private record ModuleMetadata(
            String title,
            String description,
            Long levelId,
            Long categoryId,
            String coverImageUrl) {

        private static ModuleMetadata from(CreateModuleRequest request) {
            return new ModuleMetadata(
                    request.title(),
                    request.description(),
                    request.levelId(),
                    request.categoryId(),
                    request.coverImageUrl());
        }

        private static ModuleMetadata from(UpdateModuleRequest request) {
            return new ModuleMetadata(
                    request.title(),
                    request.description(),
                    request.levelId(),
                    request.categoryId(),
                    request.coverImageUrl());
        }
    }
}
