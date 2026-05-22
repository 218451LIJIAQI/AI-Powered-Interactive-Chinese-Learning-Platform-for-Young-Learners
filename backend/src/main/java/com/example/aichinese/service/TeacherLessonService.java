package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.CreateLessonRequest;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.content.UpdateLessonRequest;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class TeacherLessonService {

    private final LessonRepository lessonRepository;
    private final TeacherModuleService teacherModuleService;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;

    public TeacherLessonService(
            LessonRepository lessonRepository,
            TeacherModuleService teacherModuleService,
            TeacherContentAccessService teacherContentAccessService,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService) {
        this.lessonRepository = lessonRepository;
        this.teacherModuleService = teacherModuleService;
        this.teacherContentAccessService = teacherContentAccessService;
        this.contentManagementMapper = contentManagementMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> getTeacherLessons(SecurityUserDetails currentUser, int page, int size) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<Lesson> lessonPage = lessonRepository.findByTeacherUserIdOrderByUpdatedAtDesc(
                currentUser.getUserId(),
                PageRequest.of(safePage, safeSize));
        List<LessonResponse> items = lessonPage.getContent().stream()
                .map(this::toLessonResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, lessonPage.getTotalElements(), lessonPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LessonResponse getTeacherLesson(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return toLessonResponse(findOwnedLesson(currentUser, lessonId));
    }

    @Transactional
    public LessonResponse createLesson(SecurityUserDetails currentUser, CreateLessonRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        Lesson lesson = new Lesson();
        teacherContentAccessService.attachLessonMetadata(
                lesson,
                teacherProfile,
                resolveOwnedModule(currentUser, request.moduleId()),
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.basicContent(),
                request.accessType(),
                request.price(),
                request.learningType(),
                request.estimatedDurationMinutes(),
                request.coverImageUrl());
        // No admin review flow: teacher-created lessons are immediately visible
        // to enrolled students.
        lesson.setContentStatus(ContentStatus.PUBLISHED);
        lesson.setRejectionReason(null);

        Lesson saved = lessonRepository.save(lesson);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_CREATE",
                "Lesson",
                saved.getId(),
                "Teacher created and published a lesson.");
        return toLessonResponse(saved);
    }

    @Transactional
    public LessonResponse updateLesson(SecurityUserDetails currentUser, Long lessonId, UpdateLessonRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = findOwnedLesson(currentUser, lessonId);
        teacherContentAccessService.validateEditableStatus(lesson.getContentStatus(), "Lesson");
        teacherContentAccessService.validatePrice(request.accessType(), request.price());

        teacherContentAccessService.attachLessonMetadata(
                lesson,
                teacherProfile,
                resolveOwnedModule(currentUser, request.moduleId()),
                teacherContentAccessService.requireLevel(request.levelId()),
                teacherContentAccessService.requireCategory(request.categoryId()),
                request.title(),
                request.description(),
                request.basicContent(),
                request.accessType(),
                request.price(),
                request.learningType(),
                request.estimatedDurationMinutes(),
                request.coverImageUrl());

        Lesson saved = lessonRepository.save(lesson);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_UPDATE",
                "Lesson",
                saved.getId(),
                "Teacher updated a lesson draft.");
        return toLessonResponse(saved);
    }

    @Transactional
    public LessonResponse archiveLesson(SecurityUserDetails currentUser, Long lessonId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = findOwnedLesson(currentUser, lessonId);
        teacherContentAccessService.validateArchivableStatus(lesson.getContentStatus(), "Lesson");

        lesson.setContentStatus(ContentStatus.ARCHIVED);
        Lesson saved = lessonRepository.save(lesson);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_ARCHIVE",
                "Lesson",
                saved.getId(),
                "Teacher archived a lesson.");
        return toLessonResponse(saved);
    }

    @Transactional(readOnly = true)
    public Lesson findOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        return lessonRepository.findByIdAndTeacherUserId(lessonId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson was not found."));
    }

    private LearningModule resolveOwnedModule(SecurityUserDetails currentUser, Long moduleId) {
        if (moduleId == null) {
            return null;
        }

        LearningModule module = teacherModuleService.findOwnedModule(currentUser, moduleId);
        if (module.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Archived modules cannot be attached to a lesson.");
        }
        return module;
    }

    private LessonResponse toLessonResponse(Lesson lesson) {
        return contentManagementMapper.toLessonResponse(lesson);
    }
}
