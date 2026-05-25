package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.Objects;
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
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningLevelRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

/**
 * Centralises the access-control checks every teacher-facing content
 * service shares: approved-teacher gating, ownership verification,
 * status / archival state enforcement, level / category lookup, and
 * the standard title / description / price validation.
 *
 * <h2>Why one service</h2>
 * <p>Modules, lessons, course packages, and quizzes all need the same
 * "is this caller an approved teacher who owns this row?" guard.
 * Concentrating that logic here keeps the per-resource services
 * ({@link TeacherModuleService}, {@link TeacherLessonService},
 * {@link TeacherPackageService}, {@link TeacherQuizService}) tightly
 * focused and ensures a single source of truth for ownership rules.</p>
 *
 * <h2>Status policy</h2>
 * <p>The platform now publishes teacher-authored content immediately
 * (no admin review queue), so the editable status set is
 * {@code DRAFT}, {@code PUBLISHED}, and the legacy {@code REJECTED}
 * state. Historic {@code REJECTED} rows remain editable so teachers
 * can keep their archives accessible while we migrate older content
 * forward.</p>
 *
 * <h2>Content safety</h2>
 * <p>All free-text fields (title, description, lesson body) are
 * validated for safety / length via {@link ContentSafetyService} and
 * the per-field caps below; price values flow through
 * {@code BigDecimal} so currency rounding errors cannot cross
 * boundaries.</p>
 */
@Service
public class TeacherContentAccessService {

    // Teachers create content as PUBLISHED immediately (no admin review flow).
    // They may continue to edit / archive their own PUBLISHED content; legacy
    // DRAFT / REJECTED rows from the previous review flow are also editable so
    // historic data remains accessible.
    //
    // Delegate to the canonical sets on the enum so the Javadoc contract on
    // ContentStatus ("Single source of truth mirrored by ...") is enforced by
    // the type system rather than by parallel literal lists.
    private static final Set<ContentStatus> TEACHER_EDITABLE_STATUSES = ContentStatus.TEACHER_EDITABLE;
    private static final Set<ContentStatus> TEACHER_ARCHIVABLE_STATUSES = ContentStatus.TEACHER_ARCHIVABLE;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_LESSON_CONTENT_LENGTH = 10000;

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
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Price must be greater than 0 for paid content.");
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
        module.setTitle(contentSafetyService.requireSafeText(title, MAX_TITLE_LENGTH, "Title is required."));
        module.setDescription(contentSafetyService.optionalSafeText(description, MAX_DESCRIPTION_LENGTH));
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
            BigDecimal price,
            LearningType learningType,
            Integer estimatedDurationMinutes,
            String coverImageUrl) {
        lesson.setTeacher(teacherProfile);
        lesson.setModule(module);
        lesson.setLevel(level);
        lesson.setCategory(category);
        lesson.setTitle(contentSafetyService.requireSafeText(title, MAX_TITLE_LENGTH, "Title is required."));
        lesson.setSummary(contentSafetyService.optionalSafeText(description, MAX_DESCRIPTION_LENGTH));
        lesson.setLessonContent(contentSafetyService.optionalSafeText(lessonContent, MAX_LESSON_CONTENT_LENGTH));
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
            BigDecimal price,
            LearningType learningType,
            String coverImageUrl) {
        coursePackage.setTeacher(teacherProfile);
        coursePackage.setLevel(level);
        coursePackage.setCategory(category);
        coursePackage.setTitle(contentSafetyService.requireSafeText(title, MAX_TITLE_LENGTH, "Title is required."));
        coursePackage.setDescription(contentSafetyService.optionalSafeText(description, MAX_DESCRIPTION_LENGTH));
        coursePackage.setAccessType(accessType);
        coursePackage.setPrice(normalizePrice(accessType, price));
        coursePackage.setLearningType(learningType);
        coursePackage.setCoverImageUrl(TextUtils.trimToNull(coverImageUrl));
    }
}
