package com.example.aichinese.mapper;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.content.ContentSubmissionDetailResponse;
import com.example.aichinese.dto.content.ContentSubmissionResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.content.PackageItemResponse;
import com.example.aichinese.dto.content.PublicCourseDetailResponse;
import com.example.aichinese.dto.content.PublicCourseItemSummaryResponse;
import com.example.aichinese.dto.content.PublicCourseSummaryResponse;
import com.example.aichinese.dto.content.PublicTeacherSummaryResponse;
import com.example.aichinese.dto.content.TeacherOwnerSummaryResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Translates the published content domain ({@link Lesson} /
 * {@link LearningModule} / {@link CoursePackage} / {@link PackageItem})
 * into the response DTOs consumed by the teacher dashboard, the admin
 * moderation queue and the public discovery catalog.
 *
 * <p>Every method is null-defensive at the boundary — a {@code null}
 * entity simply returns {@code null} rather than throwing — so that
 * partial domain graphs (e.g. a lesson with no module, or a teacher
 * profile whose {@code user} association failed to hydrate) degrade
 * gracefully to a missing field on the wire instead of a 500.</p>
 *
 * <p>The public-view mappers
 * ({@link #toPublicCourseSummary(Lesson, String)} /
 * {@link #toPublicCourseDetail(CoursePackage, List, String)}) deliberately keep
 * the locked / unlocked decision inside this class so the controller
 * layer never has to repeat the
 * {@code accessType == PAID} check at every callsite — paid items get a
 * stable, user-friendly placeholder message in the
 * {@code accessLockedReason} field which the front-end displays in the
 * locked-content card.</p>
 */
@Component
public class ContentManagementMapper {

    /**
     * Friendly explanation surfaced on every {@code PAID} public-catalog
     * response. The platform's payment integration is fully wired (Stripe
     * webhook + mock provider), so the gate is now "sign in &amp; enrol"
     * rather than "feature not yet built". Centralised so copy edits do
     * not require a service-layer change.
     */
    private static final String PAID_LOCKED_REASON =
            "Sign in and enrol to unlock this paid content.";

    /**
     * Stable client-side identifier prefix for {@link Lesson} entries in
     * the public catalogue. The frontend uses {@code id} (not
     * {@code resourceId}) as the routing key when the catalogue mixes
     * lessons and packages, so a stable prefix prevents collisions
     * between a lesson with id 42 and a package with id 42.
     */
    private static final String LESSON_ID_PREFIX = "LESSON-";

    /** Stable client-side identifier prefix for {@link CoursePackage} entries. */
    private static final String PACKAGE_ID_PREFIX = "PACKAGE-";

    /** Call-to-action shown to anonymous visitors viewing a single lesson. */
    private static final String LESSON_CTA = "Login to enrol";

    /** Call-to-action shown to anonymous visitors viewing a course package. */
    private static final String PACKAGE_CTA = "Register to start learning";

    public LearningLevelResponse toLevelResponse(LearningLevel level) {
        if (level == null) {
            return null;
        }

        return new LearningLevelResponse(level.getId(), level.getName(), level.getDescription());
    }

    public LearningCategoryResponse toCategoryResponse(LearningCategory category) {
        if (category == null) {
            return null;
        }

        return new LearningCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    public TeacherOwnerSummaryResponse toTeacherSummary(TeacherProfile teacherProfile) {
        if (teacherProfile == null || teacherProfile.getUser() == null) {
            return null;
        }

        return new TeacherOwnerSummaryResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getTeacherStatus());
    }

    public LearningModuleResponse toLearningModuleResponse(LearningModule module) {
        if (module == null) {
            return null;
        }
        return new LearningModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                toLevelResponse(module.getLevel()),
                toCategoryResponse(module.getCategory()),
                module.getAccessType(),
                module.getPrice(),
                module.getCoverImageUrl(),
                module.getContentStatus(),
                module.getRejectionReason(),
                toTeacherSummary(module.getTeacher()),
                module.getCreatedAt(),
                module.getUpdatedAt());
    }

    public LessonResponse toLessonResponse(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        LearningModule module = lesson.getModule();
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                module == null ? null : module.getId(),
                module == null ? null : module.getTitle(),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                lesson.getEstimatedDurationMinutes(),
                lesson.getCoverImageUrl(),
                lesson.getContentStatus(),
                lesson.getRejectionReason(),
                toTeacherSummary(lesson.getTeacher()),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    public PackageItemResponse toPackageItemResponse(PackageItem item) {
        if (item == null) {
            return null;
        }
        CoursePackage parent = item.getCoursePackage();
        return new PackageItemResponse(
                item.getId(),
                parent == null ? null : parent.getId(),
                item.getItemType(),
                item.getItemId(),
                item.getSortOrder(),
                item.getTitleSnapshot());
    }

    public CoursePackageResponse toCoursePackageResponse(CoursePackage coursePackage, List<PackageItem> items) {
        if (coursePackage == null) {
            return null;
        }
        List<PackageItem> safeItems = items == null ? List.of() : items;
        return new CoursePackageResponse(
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getAccessType(),
                coursePackage.getPrice(),
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus(),
                coursePackage.getRejectionReason(),
                toTeacherSummary(coursePackage.getTeacher()),
                safeItems.stream()
                        .filter(Objects::nonNull)
                        .map(this::toPackageItemResponse)
                        .toList(),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    public ContentSubmissionResponse toSubmissionResponse(LearningModule module) {
        if (module == null) {
            return null;
        }
        return new ContentSubmissionResponse(
                ManagedContentType.MODULE,
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                toTeacherSummary(module.getTeacher()),
                toLevelResponse(module.getLevel()),
                toCategoryResponse(module.getCategory()),
                null,
                module.getAccessType(),
                module.getPrice(),
                module.getContentStatus(),
                module.getRejectionReason(),
                module.getCreatedAt(),
                module.getUpdatedAt());
    }

    public ContentSubmissionResponse toSubmissionResponse(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        return new ContentSubmissionResponse(
                ManagedContentType.LESSON,
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                toTeacherSummary(lesson.getTeacher()),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                lesson.getContentStatus(),
                lesson.getRejectionReason(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    public ContentSubmissionResponse toSubmissionResponse(CoursePackage coursePackage) {
        if (coursePackage == null) {
            return null;
        }
        return new ContentSubmissionResponse(
                ManagedContentType.PACKAGE,
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                toTeacherSummary(coursePackage.getTeacher()),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getAccessType(),
                coursePackage.getPrice(),
                coursePackage.getContentStatus(),
                coursePackage.getRejectionReason(),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(LearningModule module, List<PackageItem> packageItems) {
        if (module == null) {
            return null;
        }
        return new ContentSubmissionDetailResponse(
                ManagedContentType.MODULE,
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                null,
                null,
                null,
                toTeacherSummary(module.getTeacher()),
                toLevelResponse(module.getLevel()),
                toCategoryResponse(module.getCategory()),
                null,
                module.getAccessType(),
                module.getPrice(),
                module.getCoverImageUrl(),
                module.getContentStatus(),
                module.getRejectionReason(),
                mapPackageItems(packageItems),
                module.getCreatedAt(),
                module.getUpdatedAt());
    }

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        LearningModule module = lesson.getModule();
        return new ContentSubmissionDetailResponse(
                ManagedContentType.LESSON,
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                module == null ? null : module.getId(),
                module == null ? null : module.getTitle(),
                toTeacherSummary(lesson.getTeacher()),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                lesson.getCoverImageUrl(),
                lesson.getContentStatus(),
                lesson.getRejectionReason(),
                List.of(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(CoursePackage coursePackage, List<PackageItem> items) {
        if (coursePackage == null) {
            return null;
        }
        return new ContentSubmissionDetailResponse(
                ManagedContentType.PACKAGE,
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                null,
                null,
                null,
                toTeacherSummary(coursePackage.getTeacher()),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getAccessType(),
                coursePackage.getPrice(),
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus(),
                coursePackage.getRejectionReason(),
                mapPackageItems(items),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    public PublicTeacherSummaryResponse toPublicTeacherSummary(TeacherProfile teacherProfile) {
        if (teacherProfile == null || teacherProfile.getUser() == null) {
            return null;
        }
        return new PublicTeacherSummaryResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED);
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(Lesson lesson, String currencyCode) {
        if (lesson == null) {
            return null;
        }
        return new PublicCourseSummaryResponse(
                LESSON_ID_PREFIX + lesson.getId(),
                lesson.getId(),
                ManagedContentType.LESSON,
                lesson.getTitle(),
                lesson.getSummary(),
                toPublicTeacherSummary(lesson.getTeacher()),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                currencyCode,
                null,
                lesson.getCoverImageUrl(),
                lesson.getAccessType() == AccessType.PAID,
                lesson.getContentStatus());
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(CoursePackage coursePackage, String currencyCode) {
        if (coursePackage == null) {
            return null;
        }
        return new PublicCourseSummaryResponse(
                PACKAGE_ID_PREFIX + coursePackage.getId(),
                coursePackage.getId(),
                ManagedContentType.PACKAGE,
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                toPublicTeacherSummary(coursePackage.getTeacher()),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getAccessType(),
                coursePackage.getPrice(),
                currencyCode,
                null,
                coursePackage.getCoverImageUrl(),
                coursePackage.getAccessType() == AccessType.PAID,
                ContentStatus.PUBLISHED);
    }

    public PublicCourseDetailResponse toPublicCourseDetail(Lesson lesson, String currencyCode) {
        if (lesson == null) {
            return null;
        }
        boolean locked = lesson.getAccessType() == AccessType.PAID;
        return new PublicCourseDetailResponse(
                LESSON_ID_PREFIX + lesson.getId(),
                lesson.getId(),
                ManagedContentType.LESSON,
                lesson.getTitle(),
                lesson.getSummary(),
                toPublicTeacherSummary(lesson.getTeacher()),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                currencyCode,
                lesson.getCoverImageUrl(),
                locked,
                lesson.getContentStatus(),
                lesson.getLessonContent(),
                List.of(),
                LESSON_CTA,
                locked ? PAID_LOCKED_REASON : null);
    }

    public PublicCourseDetailResponse toPublicCourseDetail(
            CoursePackage coursePackage,
            List<PackageItem> items,
            String currencyCode) {
        if (coursePackage == null) {
            return null;
        }
        List<PackageItem> safeItems = items == null ? List.of() : items;
        boolean locked = coursePackage.getAccessType() == AccessType.PAID;
        return new PublicCourseDetailResponse(
                PACKAGE_ID_PREFIX + coursePackage.getId(),
                coursePackage.getId(),
                ManagedContentType.PACKAGE,
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                toPublicTeacherSummary(coursePackage.getTeacher()),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getAccessType(),
                coursePackage.getPrice(),
                currencyCode,
                coursePackage.getCoverImageUrl(),
                locked,
                coursePackage.getContentStatus(),
                null,
                safeItems.stream()
                        .filter(Objects::nonNull)
                        .map(this::toPublicCourseItemSummary)
                        .toList(),
                PACKAGE_CTA,
                locked ? PAID_LOCKED_REASON : null);
    }

    /**
     * Maps a single {@link PackageItem} to the public-catalogue item
     * summary the discovery UI renders inside a course-package detail
     * card. Extracted from the inline lambda inside
     * {@link #toPublicCourseDetail(CoursePackage, List, String)} so the
     * mapping rule lives next to its sibling helpers and is unit-test
     * reachable.
     */
    private PublicCourseItemSummaryResponse toPublicCourseItemSummary(PackageItem item) {
        return new PublicCourseItemSummaryResponse(
                mapPackageItemType(item),
                item.getItemId(),
                item.getTitleSnapshot(),
                item.getSortOrder());
    }

    /**
     * Defensive null-safe stream mapper for {@link PackageItem} lists
     * passed into the submission-detail helpers. Returns an immutable
     * empty list when the input is {@code null} so the response record
     * always carries a non-{@code null} {@code packageItems} field.
     */
    private List<PackageItemResponse> mapPackageItems(List<PackageItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(this::toPackageItemResponse)
                .toList();
    }

    private static ManagedContentType mapPackageItemType(PackageItem item) {
        return switch (item.getItemType()) {
            case MODULE -> ManagedContentType.MODULE;
            case LESSON -> ManagedContentType.LESSON;
        };
    }
}
