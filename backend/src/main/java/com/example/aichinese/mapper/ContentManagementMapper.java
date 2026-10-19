package com.example.aichinese.mapper;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
import com.example.aichinese.entity.enums.ManagedContentType;
import com.example.aichinese.entity.enums.TeacherStatus;

@Component
public class ContentManagementMapper {

    private static final String LESSON_ID_PREFIX = "LESSON-";

    private static final String PACKAGE_ID_PREFIX = "PACKAGE-";

    private static LearningLevelResponse toLevelResponse(LearningLevel level) {
        if (level == null) {
            return null;
        }

        return new LearningLevelResponse(level.getId(), level.getName(), level.getDescription());
    }

    private static LearningCategoryResponse toCategoryResponse(LearningCategory category) {
        if (category == null) {
            return null;
        }

        return new LearningCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    private static TeacherOwnerSummaryResponse toTeacherSummary(TeacherProfile teacherProfile) {
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
                lesson.getEstimatedDurationMinutes(),
                lesson.getCoverImageUrl(),
                lesson.getContentStatus(),
                lesson.getRejectionReason(),
                toTeacherSummary(lesson.getTeacher()),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    private PackageItemResponse toPackageItemResponse(PackageItem item) {
        if (item == null) {
            return null;
        }
        CoursePackage owner = item.getCoursePackage();
        return new PackageItemResponse(
                item.getId(),
                owner == null ? null : owner.getId(),
                item.getItemType(),
                item.getItemId(),
                item.getSortOrder(),
                item.getTitleSnapshot());
    }

    public CoursePackageResponse toCoursePackageResponse(CoursePackage coursePackage, List<PackageItem> items) {
        if (coursePackage == null) {
            return null;
        }
        return new CoursePackageResponse(
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                toLevelResponse(coursePackage.getLevel()),
                toCategoryResponse(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus(),
                coursePackage.getRejectionReason(),
                toTeacherSummary(coursePackage.getTeacher()),
                mapPackageItems(items),
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
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus(),
                coursePackage.getRejectionReason(),
                mapPackageItems(items),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    private static PublicTeacherSummaryResponse toPublicTeacherSummary(TeacherProfile teacherProfile) {
        if (teacherProfile == null || teacherProfile.getUser() == null) {
            return null;
        }
        return new PublicTeacherSummaryResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getTeacherStatus() == TeacherStatus.APPROVED);
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(Lesson lesson) {
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
                lesson.getCoverImageUrl(),
                lesson.getContentStatus());
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(CoursePackage coursePackage) {
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
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus());
    }

    public PublicCourseDetailResponse toPublicCourseDetail(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
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
                lesson.getCoverImageUrl(),
                lesson.getContentStatus(),
                // D1/D17: public visitors get summaries only, never the full lesson body.
                null,
                List.of());
    }

    public PublicCourseDetailResponse toPublicCourseDetail(
            CoursePackage coursePackage,
            List<PackageItem> items) {
        if (coursePackage == null) {
            return null;
        }
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
                coursePackage.getCoverImageUrl(),
                coursePackage.getContentStatus(),
                null,
                mapItems(items, this::toPublicCourseItemSummary));
    }

    private PublicCourseItemSummaryResponse toPublicCourseItemSummary(PackageItem item) {
        return new PublicCourseItemSummaryResponse(
                mapPackageItemType(item),
                item.getItemId(),
                item.getTitleSnapshot(),
                item.getSortOrder());
    }

    private List<PackageItemResponse> mapPackageItems(List<PackageItem> items) {
        return mapItems(items, this::toPackageItemResponse);
    }

    private static <R> List<R> mapItems(List<PackageItem> items, Function<? super PackageItem, R> mapper) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(mapper)
                .toList();
    }

    private static ManagedContentType mapPackageItemType(PackageItem item) {
        return switch (item.getItemType()) {
            case MODULE -> ManagedContentType.MODULE;
            case LESSON -> ManagedContentType.LESSON;
        };
    }
}
