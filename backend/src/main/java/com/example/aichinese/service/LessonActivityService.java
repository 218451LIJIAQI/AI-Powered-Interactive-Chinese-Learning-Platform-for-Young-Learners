package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.content.CreateLessonActivityRequest;
import com.example.aichinese.dto.content.LessonActivityResponse;
import com.example.aichinese.dto.content.UpdateLessonActivityRequest;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonActivity;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.repository.LessonActivityRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class LessonActivityService {

    private final LessonActivityRepository lessonActivityRepository;
    private final TeacherLessonService teacherLessonService;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ContentManagementMapper contentManagementMapper;
    private final AuditLogService auditLogService;

    public LessonActivityService(
            LessonActivityRepository lessonActivityRepository,
            TeacherLessonService teacherLessonService,
            TeacherContentAccessService teacherContentAccessService,
            ContentManagementMapper contentManagementMapper,
            AuditLogService auditLogService) {
        this.lessonActivityRepository = lessonActivityRepository;
        this.teacherLessonService = teacherLessonService;
        this.teacherContentAccessService = teacherContentAccessService;
        this.contentManagementMapper = contentManagementMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<LessonActivityResponse> getLessonActivities(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return lessonActivityRepository.findByLessonIdAndContentStatusNotOrderBySortOrderAscCreatedAtAsc(
                lesson.getId(),
                ContentStatus.ARCHIVED).stream()
                .map(contentManagementMapper::toLessonActivityResponse)
                .toList();
    }

    @Transactional
    public LessonActivityResponse createLessonActivity(
            SecurityUserDetails currentUser,
            Long lessonId,
            CreateLessonActivityRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        teacherContentAccessService.validateEditableStatus(lesson.getContentStatus(), "Lesson");

        LessonActivity activity = new LessonActivity();
        activity.setLesson(lesson);
        activity.setTitle(request.title().trim());
        activity.setActivityType(request.activityType());
        activity.setInstructions(TextUtils.trimToNull(request.instruction()));
        activity.setDescription(TextUtils.trimToNull(request.description()));
        activity.setContentPayload(TextUtils.trimToNull(request.placeholderContent()));
        activity.setSortOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        activity.setContentStatus(lesson.getContentStatus());

        LessonActivity saved = lessonActivityRepository.save(activity);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_ACTIVITY_CREATE",
                "LessonActivity",
                saved.getId(),
                "Teacher created a lesson activity placeholder.");
        return contentManagementMapper.toLessonActivityResponse(saved);
    }

    @Transactional
    public LessonActivityResponse updateLessonActivity(
            SecurityUserDetails currentUser,
            Long activityId,
            UpdateLessonActivityRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LessonActivity activity = lessonActivityRepository.findByIdAndLessonTeacherUserId(activityId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson activity was not found."));
        teacherContentAccessService.validateEditableStatus(activity.getLesson().getContentStatus(), "Lesson");

        activity.setTitle(request.title().trim());
        activity.setActivityType(request.activityType());
        activity.setInstructions(TextUtils.trimToNull(request.instruction()));
        activity.setDescription(TextUtils.trimToNull(request.description()));
        activity.setContentPayload(TextUtils.trimToNull(request.placeholderContent()));
        activity.setSortOrder(request.displayOrder() == null ? 0 : request.displayOrder());

        LessonActivity saved = lessonActivityRepository.save(activity);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_ACTIVITY_UPDATE",
                "LessonActivity",
                saved.getId(),
                "Teacher updated a lesson activity placeholder.");
        return contentManagementMapper.toLessonActivityResponse(saved);
    }

    @Transactional
    public LessonActivityResponse archiveLessonActivity(SecurityUserDetails currentUser, Long activityId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        LessonActivity activity = lessonActivityRepository.findByIdAndLessonTeacherUserId(activityId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested lesson activity was not found."));
        teacherContentAccessService.validateEditableStatus(activity.getLesson().getContentStatus(), "Lesson");

        // Idempotency guard mirroring AdminContentReviewService.archiveContent
        // and RecordedLessonService.archiveRecordedVideo: refuse double-archive
        // so a teacher double-clicking the button does not write a duplicate
        // audit-log row.
        if (activity.getContentStatus() == ContentStatus.ARCHIVED) {
            return contentManagementMapper.toLessonActivityResponse(activity);
        }

        activity.setContentStatus(ContentStatus.ARCHIVED);
        LessonActivity saved = lessonActivityRepository.save(activity);
        auditLogService.record(
                teacherProfile.getUser(),
                "TEACHER_LESSON_ACTIVITY_ARCHIVE",
                "LessonActivity",
                saved.getId(),
                "Teacher archived a lesson activity placeholder.");
        return contentManagementMapper.toLessonActivityResponse(saved);
    }
}
