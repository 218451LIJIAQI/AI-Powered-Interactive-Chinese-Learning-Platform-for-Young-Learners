package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class TeacherContentAccessService {

    // Teachers create content as PUBLISHED immediately (no admin review flow).
    // They may continue to edit / archive their own PUBLISHED content; legacy
    // DRAFT / REJECTED rows from the previous review flow are also editable so
    // historic data remains accessible.
    private static final Set<ContentStatus> TEACHER_EDITABLE_STATUSES =
            Set.of(ContentStatus.DRAFT, ContentStatus.PUBLISHED, ContentStatus.REJECTED);
    private static final Set<ContentStatus> TEACHER_ARCHIVABLE_STATUSES =
            Set.of(ContentStatus.DRAFT, ContentStatus.PUBLISHED, ContentStatus.REJECTED);

    private final TeacherProfileRepository teacherProfileRepository;
    private final LearningLevelRepository learningLevelRepository;
    private final LearningCategoryRepository learningCategoryRepository;

    public TeacherContentAccessService(
            TeacherProfileRepository teacherProfileRepository,
            LearningLevelRepository learningLevelRepository,
            LearningCategoryRepository learningCategoryRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.learningLevelRepository = learningLevelRepository;
        this.learningCategoryRepository = learningCategoryRepository;
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

    public void validateEditableStatus(ContentStatus currentStatus, String contentLabel) {
        if (!TEACHER_EDITABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    contentLabel + " cannot be edited once it has been archived.");
        }
    }

    public void validateArchivableStatus(ContentStatus currentStatus, String contentLabel) {
        if (!TEACHER_ARCHIVABLE_STATUSES.contains(currentStatus)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    contentLabel + " has already been archived.");
        }
    }

    public void validatePrice(AccessType accessType, BigDecimal price) {
        if (accessType == AccessType.PAID) {
            if (price == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Price is required for paid content.");
            }
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Price must be greater than or equal to 0.");
            }
            return;
        }

        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Free content must not have a positive price.");
        }
    }

    private BigDecimal normalizePrice(AccessType accessType, BigDecimal price) {
        if (accessType == AccessType.FREE) {
            return BigDecimal.ZERO;
        }

        return price == null ? BigDecimal.ZERO : price;
    }

    public void attachModuleMetadata(
            LearningModule module,
            TeacherProfile teacherProfile,
            LearningLevel level,
            LearningCategory category,
            String title,
            String description,
            AccessType accessType,
            BigDecimal price,
            String coverImageUrl) {
        module.setTeacher(teacherProfile);
        module.setLevel(level);
        module.setCategory(category);
        module.setTitle(title.trim());
        module.setDescription(TextUtils.trimToNull(description));
        module.setAccessType(accessType);
        module.setPrice(normalizePrice(accessType, price));
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
            AccessType accessType,
            java.math.BigDecimal price,
            com.example.aichinese.entity.enums.LearningType learningType,
            Integer estimatedDurationMinutes,
            String coverImageUrl) {
        lesson.setTeacher(teacherProfile);
        lesson.setModule(module);
        lesson.setLevel(level);
        lesson.setCategory(category);
        lesson.setTitle(title.trim());
        lesson.setSummary(TextUtils.trimToNull(description));
        lesson.setLessonContent(TextUtils.trimToNull(lessonContent));
        lesson.setAccessType(accessType);
        lesson.setPrice(normalizePrice(accessType, price));
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
            AccessType accessType,
            java.math.BigDecimal price,
            com.example.aichinese.entity.enums.LearningType learningType,
            String coverImageUrl) {
        coursePackage.setTeacher(teacherProfile);
        coursePackage.setLevel(level);
        coursePackage.setCategory(category);
        coursePackage.setTitle(title.trim());
        coursePackage.setDescription(TextUtils.trimToNull(description));
        coursePackage.setAccessType(accessType);
        coursePackage.setPrice(normalizePrice(accessType, price));
        coursePackage.setLearningType(learningType);
        coursePackage.setCoverImageUrl(TextUtils.trimToNull(coverImageUrl));
    }
}
