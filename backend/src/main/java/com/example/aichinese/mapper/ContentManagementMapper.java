package com.example.aichinese.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.content.ContentSubmissionDetailResponse;
import com.example.aichinese.dto.content.ContentSubmissionResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.LessonActivityResponse;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.content.PackageItemResponse;
import com.example.aichinese.dto.content.PublicActivitySummaryResponse;
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
import com.example.aichinese.entity.LessonActivity;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.ManagedContentType;

@Component
public class ContentManagementMapper {

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

    public LessonActivityResponse toLessonActivityResponse(LessonActivity activity) {
        return new LessonActivityResponse(
                activity.getId(),
                activity.getLesson().getId(),
                activity.getTitle(),
                activity.getActivityType(),
                activity.getInstructions(),
                activity.getDescription(),
                activity.getSortOrder(),
                activity.getContentPayload(),
                activity.getContentStatus(),
                activity.getCreatedAt(),
                activity.getUpdatedAt());
    }

    public LessonResponse toLessonResponse(Lesson lesson, List<LessonActivity> activities) {
        return new LessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                lesson.getModule() != null ? lesson.getModule().getId() : null,
                lesson.getModule() != null ? lesson.getModule().getTitle() : null,
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
                activities.stream().map(this::toLessonActivityResponse).toList(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    public PackageItemResponse toPackageItemResponse(PackageItem item) {
        return new PackageItemResponse(
                item.getId(),
                item.getCoursePackage().getId(),
                item.getItemType(),
                item.getItemId(),
                item.getSortOrder(),
                item.getTitleSnapshot());
    }

    public CoursePackageResponse toCoursePackageResponse(CoursePackage coursePackage, List<PackageItem> items) {
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
                items.stream().map(this::toPackageItemResponse).toList(),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    public ContentSubmissionResponse toSubmissionResponse(LearningModule module) {
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

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(
            LearningModule module,
            List<LessonActivity> activities,
            List<PackageItem> packageItems) {
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
                activities.stream().map(this::toLessonActivityResponse).toList(),
                packageItems.stream().map(this::toPackageItemResponse).toList(),
                module.getCreatedAt(),
                module.getUpdatedAt());
    }

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(Lesson lesson, List<LessonActivity> activities) {
        return new ContentSubmissionDetailResponse(
                ManagedContentType.LESSON,
                lesson.getId(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonContent(),
                lesson.getModule() != null ? lesson.getModule().getId() : null,
                lesson.getModule() != null ? lesson.getModule().getTitle() : null,
                toTeacherSummary(lesson.getTeacher()),
                toLevelResponse(lesson.getLevel()),
                toCategoryResponse(lesson.getCategory()),
                lesson.getLearningType(),
                lesson.getAccessType(),
                lesson.getPrice(),
                lesson.getCoverImageUrl(),
                lesson.getContentStatus(),
                lesson.getRejectionReason(),
                activities.stream().map(this::toLessonActivityResponse).toList(),
                List.of(),
                lesson.getCreatedAt(),
                lesson.getUpdatedAt());
    }

    public ContentSubmissionDetailResponse toSubmissionDetailResponse(CoursePackage coursePackage, List<PackageItem> items) {
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
                List.of(),
                items.stream().map(this::toPackageItemResponse).toList(),
                coursePackage.getCreatedAt(),
                coursePackage.getUpdatedAt());
    }

    public PublicTeacherSummaryResponse toPublicTeacherSummary(TeacherProfile teacherProfile) {
        return new PublicTeacherSummaryResponse(
                teacherProfile.getId(),
                teacherProfile.getUser().getDisplayName(),
                teacherProfile.getTeacherStatus() == com.example.aichinese.entity.enums.TeacherStatus.APPROVED);
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(Lesson lesson) {
        return new PublicCourseSummaryResponse(
                "LESSON-" + lesson.getId(),
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
                BigDecimal.ZERO,
                lesson.getCoverImageUrl(),
                lesson.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID,
                lesson.getContentStatus());
    }

    public PublicCourseSummaryResponse toPublicCourseSummary(CoursePackage coursePackage) {
        return new PublicCourseSummaryResponse(
                "PACKAGE-" + coursePackage.getId(),
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
                BigDecimal.ZERO,
                coursePackage.getCoverImageUrl(),
                coursePackage.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID,
                ContentStatus.PUBLISHED);
    }

    public PublicCourseDetailResponse toPublicCourseDetail(Lesson lesson, List<LessonActivity> activities) {
        return new PublicCourseDetailResponse(
                "LESSON-" + lesson.getId(),
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
                lesson.getCoverImageUrl(),
                lesson.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID,
                lesson.getContentStatus(),
                lesson.getLessonContent(),
                List.of(),
                activities.stream()
                        .filter(activity -> activity.getContentStatus() != ContentStatus.ARCHIVED)
                        .map(activity -> new PublicActivitySummaryResponse(
                                activity.getId(),
                                activity.getTitle(),
                                activity.getActivityType(),
                                activity.getInstructions(),
                                activity.getSortOrder()))
                        .toList(),
                "Login to enrol",
                lesson.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID
                        ? "This paid content is locked until payment workflows are implemented."
                        : null);
    }

    public PublicCourseDetailResponse toPublicCourseDetail(CoursePackage coursePackage, List<PackageItem> items) {
        return new PublicCourseDetailResponse(
                "PACKAGE-" + coursePackage.getId(),
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
                coursePackage.getCoverImageUrl(),
                coursePackage.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID,
                coursePackage.getContentStatus(),
                null,
                items.stream()
                        .map(item -> new PublicCourseItemSummaryResponse(
                                mapPackageItemType(item),
                                item.getItemId(),
                                item.getTitleSnapshot(),
                                item.getSortOrder()))
                        .toList(),
                List.of(),
                "Register to start learning",
                coursePackage.getAccessType() == com.example.aichinese.entity.enums.AccessType.PAID
                        ? "This paid content is locked until payment workflows are implemented."
                        : null);
    }

    private ManagedContentType mapPackageItemType(PackageItem item) {
        return switch (item.getItemType()) {
            case MODULE -> ManagedContentType.MODULE;
            case LESSON -> ManagedContentType.LESSON;
            case ACTIVITY -> ManagedContentType.LESSON;
        };
    }
}
