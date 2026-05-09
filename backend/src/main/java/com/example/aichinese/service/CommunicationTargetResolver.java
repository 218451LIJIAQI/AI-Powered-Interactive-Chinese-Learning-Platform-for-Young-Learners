package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Review;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentModerationTargetType;
import com.example.aichinese.entity.enums.ReportTargetType;
import com.example.aichinese.entity.enums.ReviewTargetType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.ReviewRepository;
import com.example.aichinese.repository.TeacherProfileRepository;

@Service
public class CommunicationTargetResolver {

    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final ReviewRepository reviewRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public CommunicationTargetResolver(
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            ReviewRepository reviewRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.lessonRepository = lessonRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.reviewRepository = reviewRepository;
        this.teacherProfileRepository = teacherProfileRepository;
    }

    @Transactional(readOnly = true)
    public ResolvedTarget resolveReportTarget(String rawTargetType, Long targetId) {
        ReportTargetType targetType = parseReportTargetType(rawTargetType);
        return switch (targetType) {
            case LESSON -> {
                Lesson lesson = requireLesson(targetId);
                yield new ResolvedTarget(targetType.name(), lesson.getId(), lesson.getTitle(), lesson.getTeacher(), lesson, null, null, null);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = requirePackage(targetId);
                yield new ResolvedTarget(targetType.name(), coursePackage.getId(), coursePackage.getTitle(), coursePackage.getTeacher(), null, coursePackage, null, null);
            }
            case REVIEW -> {
                Review review = requireReview(targetId);
                yield new ResolvedTarget(targetType.name(), review.getId(), "Review #" + review.getId(), resolveReviewTeacher(review), null, null, review, null);
            }
            case TEACHER -> {
                TeacherProfile teacher = requireTeacherProfile(targetId);
                yield new ResolvedTarget(targetType.name(), teacher.getId(), teacher.getUser().getDisplayName(), teacher, null, null, null, teacher);
            }
            case TECHNICAL, OTHER -> new ResolvedTarget(targetType.name(), targetId, null, null, null, null, null, null);
        };
    }

    @Transactional(readOnly = true)
    public ResolvedTarget resolveReviewTarget(String rawTargetType, Long targetId) {
        ReviewTargetType targetType = parseReviewTargetType(rawTargetType);
        return switch (targetType) {
            case LESSON -> {
                Lesson lesson = requireLesson(targetId);
                yield new ResolvedTarget(targetType.name(), lesson.getId(), lesson.getTitle(), lesson.getTeacher(), lesson, null, null, null);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = requirePackage(targetId);
                yield new ResolvedTarget(targetType.name(), coursePackage.getId(), coursePackage.getTitle(), coursePackage.getTeacher(), null, coursePackage, null, null);
            }
        };
    }

    @Transactional(readOnly = true)
    public ResolvedTarget resolveModerationTarget(String rawTargetType, Long targetId) {
        ContentModerationTargetType targetType = parseModerationTargetType(rawTargetType);
        return switch (targetType) {
            case LESSON -> {
                Lesson lesson = requireLesson(targetId);
                yield new ResolvedTarget(targetType.name(), lesson.getId(), lesson.getTitle(), lesson.getTeacher(), lesson, null, null, null);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = requirePackage(targetId);
                yield new ResolvedTarget(targetType.name(), coursePackage.getId(), coursePackage.getTitle(), coursePackage.getTeacher(), null, coursePackage, null, null);
            }
            case REVIEW -> {
                Review review = requireReview(targetId);
                yield new ResolvedTarget(targetType.name(), review.getId(), "Review #" + review.getId(), resolveReviewTeacher(review), null, null, review, null);
            }
            case TEACHER_PROFILE -> {
                TeacherProfile teacher = requireTeacherProfile(targetId);
                yield new ResolvedTarget(targetType.name(), teacher.getId(), teacher.getUser().getDisplayName(), teacher, null, null, null, teacher);
            }
            case OTHER -> new ResolvedTarget(targetType.name(), targetId, null, null, null, null, null, null);
        };
    }

    private Lesson requireLesson(Long lessonId) {
        if (lessonId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id is required.");
        }
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lesson was not found."));
    }

    private CoursePackage requirePackage(Long packageId) {
        if (packageId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id is required.");
        }
        return coursePackageRepository.findById(packageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Package was not found."));
    }

    private Review requireReview(Long reviewId) {
        if (reviewId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id is required.");
        }
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Review was not found."));
    }

    private TeacherProfile requireTeacherProfile(Long teacherId) {
        if (teacherId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id is required.");
        }
        return teacherProfileRepository.findDetailedById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    private TeacherProfile resolveReviewTeacher(Review review) {
        if (review.getTargetType() == null || review.getTargetId() == null) {
            return null;
        }

        String normalizedType = review.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("LESSON".equals(normalizedType)) {
            return lessonRepository.findById(review.getTargetId()).map(Lesson::getTeacher).orElse(null);
        }
        if ("PACKAGE".equals(normalizedType)) {
            return coursePackageRepository.findById(review.getTargetId()).map(CoursePackage::getTeacher).orElse(null);
        }
        return null;
    }

    private ReportTargetType parseReportTargetType(String rawTargetType) {
        try {
            return ReportTargetType.valueOf(rawTargetType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid report target type.");
        }
    }

    private ReviewTargetType parseReviewTargetType(String rawTargetType) {
        try {
            return ReviewTargetType.valueOf(rawTargetType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid review target type.");
        }
    }

    private ContentModerationTargetType parseModerationTargetType(String rawTargetType) {
        try {
            return ContentModerationTargetType.valueOf(rawTargetType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid moderation target type.");
        }
    }

    public record ResolvedTarget(
            String targetType,
            Long targetId,
            String targetTitle,
            TeacherProfile relatedTeacher,
            Lesson lesson,
            CoursePackage coursePackage,
            Review review,
            TeacherProfile teacherProfile) {
    }
}
