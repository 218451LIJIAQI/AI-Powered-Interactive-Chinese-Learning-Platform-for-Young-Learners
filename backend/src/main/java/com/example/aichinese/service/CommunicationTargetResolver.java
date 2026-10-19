package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.base.ReviewableContent;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.ContentModerationTargetType;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.TeacherProfileRepository;

@Service
public class CommunicationTargetResolver {

    private static final String TARGET_ID_REQUIRED = "Target id is required.";

    private final LessonRepository lessonRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final TeacherProfileRepository teacherProfileRepository;

    public CommunicationTargetResolver(
            LessonRepository lessonRepository,
            LearningModuleRepository learningModuleRepository,
            CoursePackageRepository coursePackageRepository,
            TeacherProfileRepository teacherProfileRepository) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
    }

    @Transactional(readOnly = true)
    public ResolvedTarget resolveModerationTarget(String rawTargetType, Long targetId) {
        ContentModerationTargetType targetType = parseModerationTargetType(rawTargetType);
        return switch (targetType) {
            case LESSON -> {
                Lesson lesson = requireLesson(targetId);
                yield new ResolvedTarget(targetType.name(), lesson.getId(), lesson.getTitle(), lesson.getTeacher(),
                        lesson, ReviewContentType.LESSON, null);
            }
            case MODULE -> {
                LearningModule module = requireModule(targetId);
                yield new ResolvedTarget(targetType.name(), module.getId(), module.getTitle(), module.getTeacher(),
                        module, ReviewContentType.MODULE, null);
            }
            case PACKAGE -> {
                CoursePackage coursePackage = requirePackage(targetId);
                yield new ResolvedTarget(targetType.name(), coursePackage.getId(), coursePackage.getTitle(),
                        coursePackage.getTeacher(), coursePackage, ReviewContentType.PACKAGE, null);
            }
            case TEACHER_PROFILE -> {
                TeacherProfile teacher = requireTeacherProfile(targetId);
                yield new ResolvedTarget(targetType.name(), teacher.getId(), teacher.getUser().getDisplayName(), teacher,
                        null, null, teacher);
            }
            case OTHER -> new ResolvedTarget(targetType.name(), targetId, null, null, null, null, null);
        };
    }

    private Lesson requireLesson(Long lessonId) {
        return lessonRepository.findById(requireTargetId(lessonId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lesson was not found."));
    }

    private LearningModule requireModule(Long moduleId) {
        return learningModuleRepository.findById(requireTargetId(moduleId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Module was not found."));
    }

    private CoursePackage requirePackage(Long packageId) {
        return coursePackageRepository.findById(requireTargetId(packageId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Package was not found."));
    }

    private TeacherProfile requireTeacherProfile(Long teacherId) {
        return teacherProfileRepository.findDetailedById(requireTargetId(teacherId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Teacher profile was not found."));
    }

    private static Long requireTargetId(Long targetId) {
        if (targetId == null) {
            throw new ResponseStatusException(BAD_REQUEST, TARGET_ID_REQUIRED);
        }
        if (targetId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Target id must be positive.");
        }
        return targetId;
    }

    private static ContentModerationTargetType parseModerationTargetType(String rawTargetType) {
        return ContentModerationTargetType.tryFrom(rawTargetType)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid moderation target type."));
    }

    /**
     * A resolved moderation target. {@code content}/{@code contentType} are set for reviewable
     * content (lesson, module, course); {@code teacherProfile} for TEACHER_PROFILE targets.
     */
    public record ResolvedTarget(
            String targetType,
            Long targetId,
            String targetTitle,
            TeacherProfile relatedTeacher,
            ReviewableContent content,
            ReviewContentType contentType,
            TeacherProfile teacherProfile) {

        public ResolvedTarget {
            targetType = requireText(targetType, "targetType");
            if (targetId != null && targetId <= 0) {
                throw new IllegalArgumentException("targetId must be positive when provided.");
            }
            targetTitle = optionalText(targetTitle);
        }

        private static String requireText(String value, String fieldName) {
            String stripped = Objects.requireNonNull(value, fieldName).strip();
            if (stripped.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be blank.");
            }
            return stripped;
        }

        private static String optionalText(String value) {
            if (value == null) {
                return null;
            }
            String stripped = value.strip();
            return stripped.isEmpty() ? null : stripped;
        }
    }
}