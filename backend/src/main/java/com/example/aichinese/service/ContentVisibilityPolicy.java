package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.TeacherProfileRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

/**
 * The single student-visibility rule set (decisions D4 and D13).
 *
 * <ul>
 *   <li>Only PUBLISHED items are visible.</li>
 *   <li>A lesson also needs its module (when it has one) to be PUBLISHED, so archiving or
 *       re-reviewing a module hides its lessons.</li>
 *   <li>A quiz needs a visible lesson.</li>
 *   <li>The owning teacher must be APPROVED and the account active, so rejecting or deactivating a
 *       teacher hides their content from students without deleting it.</li>
 *   <li>Practice activities authored by a teacher follow the same teacher rule; activities authored
 *       by an admin only need to be PUBLISHED.</li>
 * </ul>
 */
@Component
public class ContentVisibilityPolicy {

    private final TeacherProfileRepository teacherProfileRepository;

    public ContentVisibilityPolicy(TeacherProfileRepository teacherProfileRepository) {
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
    }

    public static boolean isActiveApprovedTeacher(TeacherProfile teacher) {
        return teacher != null
                && teacher.getTeacherStatus() == TeacherStatus.APPROVED
                && teacher.getUser() != null
                && teacher.getUser().isActive();
    }

    public static boolean isLessonVisible(Lesson lesson) {
        if (lesson == null || lesson.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        LearningModule module = lesson.getModule();
        if (module != null && module.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        return isActiveApprovedTeacher(lesson.getTeacher());
    }

    public static boolean isQuizVisible(Quiz quiz) {
        return quiz != null
                && quiz.getContentStatus() == ContentStatus.PUBLISHED
                && isLessonVisible(quiz.getLesson());
    }

    public static boolean isPackageVisible(CoursePackage coursePackage) {
        return coursePackage != null
                && coursePackage.getContentStatus() == ContentStatus.PUBLISHED
                && isActiveApprovedTeacher(coursePackage.getTeacher());
    }

    /** Activities: PUBLISHED, and when the author is a teacher that teacher must be approved and active. */
    @Transactional(readOnly = true)
    public boolean isActivityVisible(PracticeActivityContent activity) {
        if (activity == null || activity.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        User author = activity.getCreatedByUser();
        if (author == null) {
            return true;
        }
        return teacherProfileRepository.findDetailedByUserId(author.getId())
                .map(ContentVisibilityPolicy::isActiveApprovedTeacher)
                .orElse(true);
    }

    /**
     * Criteria predicate for activity lists: the author is not a teacher whose status is not
     * APPROVED, and a teacher author's account is active.
     */
    public static Predicate activityAuthorVisible(
            Path<User> author,
            CriteriaQuery<?> query,
            CriteriaBuilder cb) {
        Subquery<Long> blockedTeachers = query.subquery(Long.class);
        var teacher = blockedTeachers.from(TeacherProfile.class);
        blockedTeachers.select(teacher.get("id"))
                .where(
                        cb.equal(teacher.get("user"), author),
                        cb.or(
                                cb.notEqual(teacher.get("teacherStatus"), TeacherStatus.APPROVED),
                                cb.isFalse(teacher.get("user").get("active"))));
        return cb.not(cb.exists(blockedTeachers));
    }
}
