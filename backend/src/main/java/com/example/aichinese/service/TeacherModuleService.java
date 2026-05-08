package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.content.CreateModuleRequest;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.UpdateModuleRequest;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class TeacherModuleService {

    private final LearningModuleRepository learningModuleRepository;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;

    public TeacherModuleService(
            LearningModuleRepository learningModuleRepository,
            TeacherContentAccessService teacherContentAccessService,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService) {
        this.learningModuleRepository = learningModuleRepository;
        this.teacherContentAccessService = teacherContentAccessService;
        this.contentManagementMapper = contentManagementMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<LearningModuleResponse> getTeacherModules(SecurityUserDetails currentUser) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return learningModuleRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId()).stream()
                .map(contentManagementMapper::toLearningModuleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningModuleResponse getTeacherModule(SecurityUserDetails currentUser, Long moduleId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return contentManagementMapper.toLearningModuleResponse(findOwnedModule(currentUser, moduleId));
    }

    @Transactional
    public LearningModuleResponse createModule(SecurityUserDetails currentUser, CreateModuleRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        LearningModule module = new LearningModule();
        teacherContentAccessService.attachModuleMetadata(
                module,
                teacherProfile,
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.accessType(),
                request.price(),
                request.coverImageUrl());
        module.setContentStatus(ContentStatus.DRAFT);
        module.setRejectionReason(null);

        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_MODULE_CREATE",
                "LearningModule",
                saved.getId(),
                "Teacher created a learning module draft.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional
    public LearningModuleResponse updateModule(SecurityUserDetails currentUser, Long moduleId, UpdateModuleRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LearningModule module = findOwnedModule(currentUser, moduleId);
        teacherContentAccessService.validateEditableStatus(module.getContentStatus(), "Module");
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        teacherContentAccessService.attachModuleMetadata(
                module,
                teacherProfile,
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.accessType(),
                request.price(),
                request.coverImageUrl());

        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_MODULE_UPDATE",
                "LearningModule",
                saved.getId(),
                "Teacher updated a learning module draft.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional
    public LearningModuleResponse submitModule(SecurityUserDetails currentUser, Long moduleId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LearningModule module = findOwnedModule(currentUser, moduleId);
        teacherContentAccessService.validateSubmittableStatus(module.getContentStatus(), "Module");

        module.setContentStatus(ContentStatus.SUBMITTED);
        LearningModule saved = learningModuleRepository.save(module);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_MODULE_SUBMIT",
                "LearningModule",
                saved.getId(),
                "Teacher submitted a learning module for admin review.");
        return contentManagementMapper.toLearningModuleResponse(saved);
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
                "TEACHER_MODULE_ARCHIVE",
                "LearningModule",
                saved.getId(),
                "Teacher archived a learning module.");
        return contentManagementMapper.toLearningModuleResponse(saved);
    }

    @Transactional(readOnly = true)
    public LearningModule findOwnedModule(SecurityUserDetails currentUser, Long moduleId) {
        return learningModuleRepository.findByIdAndTeacherUserId(moduleId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested module was not found."));
    }
}
