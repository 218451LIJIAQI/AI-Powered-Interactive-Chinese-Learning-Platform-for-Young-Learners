package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentModerationTargetType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.TeacherProfileRepository;

/**
 * Resolves a {@code (targetType, targetId)} pair into the concrete
 * entity, used by moderation / notification flows that need to refer
 * to arbitrary domain rows.
 *
 * <p>Supported target types are enumerated by
 * {@link ContentModerationTargetType}. The resolver throws a
 * {@code 400} when the type string is invalid or a {@code 404} when
 * the row does not exist, so callers do not have to repeat the same
 * defensive lookups. Read-only and cheap — every method is a single
 * indexed primary-key lookup.</p>
 */
@Service
public class CommunicationTargetResolver {

    private final LessonRepository lessonRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public CommunicationTargetResolver(
            LessonRepository lessonRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
    }

    @Transactional(readOnly = true)
    public ResolvedTarget resolveModerationTarget(String rawTargetType, Long targetId) {
        ContentModerationTargetType targetType = parseModerationTargetType(rawTargetType);
        return switch (targetType) {
            case LESSON -> {
                Lesson lesson = requireLesson(targetId);
                yield new ResolvedTarget(targetType.name(), lesson.getId(), lesson.getTitle(), lesson.getTeacher(), lesson, null, null);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = requirePackage(targetId);
                yield new ResolvedTarget(targetType.name(), coursePackage.getId(), coursePackage.getTitle(), coursePackage.getTeacher(), null, coursePackage, null);
            }
            case TEACHER_PROFILE -> {
                TeacherProfile teacher = requireTeacherProfile(targetId);
                yield new ResolvedTarget(targetType.name(), teacher.getId(), teacher.getUser().getDisplayName(), teacher, null, null, teacher);
            }
            case OTHER -> new ResolvedTarget(targetType.name(), targetId, null, null, null, null, null);
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

    private TeacherProfile requireTeacherProfile(Long teacherId) {
        if (teacherId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id is required.");
        }
        return teacherProfileRepository.findDetailedById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    private ContentModerationTargetType parseModerationTargetType(String rawTargetType) {
        if (rawTargetType == null || rawTargetType.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Moderation target type is required.");
        }
        try {
            return ContentModerationTargetType.valueOf(rawTargetType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
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
            TeacherProfile teacherProfile) {
    }
}
