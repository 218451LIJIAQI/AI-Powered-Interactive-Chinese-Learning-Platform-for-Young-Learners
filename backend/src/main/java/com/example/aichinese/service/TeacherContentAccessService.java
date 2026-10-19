package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.content.ContentDtoConstants;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class TeacherContentAccessService {

    private static final Set<ContentStatus> TEACHER_ARCHIVABLE_STATUSES = ContentStatus.TEACHER_ARCHIVABLE;

    private final TeacherProfileRepository teacherProfileRepository;
    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;
    private final ContentSafetyService contentSafetyService;

    public TeacherContentAccessService(
            TeacherProfileRepository teacherProfileRepository,
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository,
            ContentSafetyService contentSafetyService) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.learningLevelRepository = Objects.requireNonNull(learningLevelRepository, "learningLevelRepository");
        this.learningCategoryRepository = Objects.requireNonNull(learningCategoryRepository, "learningCategoryRepository");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
    }

    @Transactional(readOnly = true)
    public TeacherProfile requireApprovedTeacher(SecurityUserDetails currentUser) {
        TeacherProfile teacherProfile = teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile could not be found."));

        if (teacherProfile.getTeacherStatus() != TeacherStatus.APPROVED) {
            throw new ResponseStatusException(FORBIDDEN, "Only approved teachers can create or submit learning content.");
        }

        return teacherProfile;
    }

    /** Message of the central D13 guard for every protected teacher feature. */
    public static final String APPROVED_TEACHER_FEATURE_MESSAGE = "Only approved teachers can use this teaching feature.";

    /**
     * The single teacher-approval guard (D13) used by {@code ApprovedTeacherInterceptor} for every
     * protected {@code /api/v1/teachers/**} endpoint: the teacher must be APPROVED and active.
     */
    @Transactional(readOnly = true)
    public void requireApprovedTeacherAccess(SecurityUserDetails currentUser) {
        if (!isApprovedTeacher(currentUser)) {
            throw new ResponseStatusException(FORBIDDEN, APPROVED_TEACHER_FEATURE_MESSAGE);
        }
    }

    /** Same guard as {@link #requireApprovedTeacherAccess} with the content-authoring message. */
    @Transactional(readOnly = true)
    public void requireApprovedTeacherForContent(SecurityUserDetails currentUser) {
        if (!isApprovedTeacher(currentUser)) {
            throw new ResponseStatusException(FORBIDDEN, "Only approved teachers can create or submit learning content.");
        }
    }

    /** True when the current teacher is APPROVED and active (dashboard shell for the rest). */
    @Transactional(readOnly = true)
    public boolean isApprovedTeacher(SecurityUserDetails currentUser) {
        return currentUser != null && currentUser.getUserId() != null
                && teacherProfileRepository.findDetailedByUserId(currentUser.getUserId())
                        .map(ContentVisibilityPolicy::isActiveApprovedTeacher)
                        .orElse(false);
    }

    @Transactional(readOnly = true)
    public LearningLevel requireLevel(Long levelId) {
        return learningLevelRepository.findById(levelId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected learning level does not exist."));
    }

    @Transactional(readOnly = true)
    public LearningCategory requireCategory(Long categoryId) {
        return learningCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected learning category does not exist."));
    }

    public void validateArchivableStatus(ContentStatus currentStatus, String contentLabel) {
        if (!TEACHER_ARCHIVABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(BAD_REQUEST, currentStatus == ContentStatus.ARCHIVED
                    ? contentLabel + " has already been archived."
                    : contentLabel + " cannot be archived while it is under review.");
        }
    }

    public void attachModuleMetadata(
            LearningModule module,
            TeacherProfile teacherProfile,
            LearningLevel level,
            LearningCategory category,
            String title,
            String description,
            String coverImageUrl) {
        module.setTeacher(teacherProfile);
        module.setLevel(level);
        module.setCategory(category);
        module.setTitle(contentSafetyService.requireSafeText(
                title, ContentDtoConstants.TITLE_MAX_LENGTH, "Title is required."));
        module.setDescription(contentSafetyService.optionalSafeText(
                description, ContentDtoConstants.LONG_DESCRIPTION_MAX_LENGTH));
        module.setCoverImageUrl(TextUtils.trimToNull(coverImageUrl));
    }

    public void attachLessonMetadata(
            Lesson lesson,
            TeacherProfile teacherProfile,
            LearningModule module,
            LearningLevel level,
            LearningCategory category,
            String title,
            String description,
            String lessonContent,
            LearningType learningType,
            Integer estimatedDurationMinutes,
            String coverImageUrl) {
        lesson.setTeacher(teacherProfile);
        lesson.setModule(module);
        lesson.setLevel(level);
        lesson.setCategory(category);
        lesson.setTitle(contentSafetyService.requireSafeText(
                title, ContentDtoConstants.TITLE_MAX_LENGTH, "Title is required."));
        lesson.setSummary(contentSafetyService.optionalSafeText(
                description, ContentDtoConstants.LONG_DESCRIPTION_MAX_LENGTH));
        lesson.setLessonContent(contentSafetyService.optionalSafeText(
                lessonContent, ContentDtoConstants.LESSON_CONTENT_MAX_LENGTH));
        lesson.setLearningType(learningType);
        lesson.setEstimatedDurationMinutes(estimatedDurationMinutes);
        lesson.setCoverImageUrl(TextUtils.trimToNull(coverImageUrl));
    }

    public void attachPackageMetadata(
            CoursePackage coursePackage,
            TeacherProfile teacherProfile,
            LearningLevel level,
            LearningCategory category,
            String title,
            String description,
            LearningType learningType,
            String coverImageUrl) {
        coursePackage.setTeacher(teacherProfile);
        coursePackage.setLevel(level);
        coursePackage.setCategory(category);
        coursePackage.setTitle(contentSafetyService.requireSafeText(
                title, ContentDtoConstants.TITLE_MAX_LENGTH, "Title is required."));
        coursePackage.setDescription(contentSafetyService.optionalSafeText(
                description, ContentDtoConstants.LONG_DESCRIPTION_MAX_LENGTH));
        coursePackage.setLearningType(learningType);
        coursePackage.setCoverImageUrl(TextUtils.trimToNull(coverImageUrl));
    }
}
