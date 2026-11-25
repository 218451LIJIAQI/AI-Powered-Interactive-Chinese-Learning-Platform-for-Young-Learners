package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.account.CurrentAccount;

import lombok.RequiredArgsConstructor;

/**
 * Finds what an admin edits (a class, a unit, a lesson) and takes the class's write lock. Every admin edits every class,
 * so the writes on one class, its units and its lessons run one after the other on the class row
 * ({@link CoursePackageRepository#findLockedById}). Unit and lesson locks resolve the owner class id via scalar queries
 * before locking under READ_COMMITTED so no managed entity is cached with a pre-lock snapshot. Also writes the audit
 * entries of class and lesson changes.
 */
@Component
@RequiredArgsConstructor
public class ClassAuthoringAccess {

    public static final String TARGET_CLASS = "CoursePackage";
    static final String CLASS_ARCHIVED = "Restore the class before you edit it.";
    private static final String UNIT_NOT_FOUND = "Unit was not found.";

    private final CoursePackageRepository coursePackageRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final PackageItemRepository packageItemRepository;
    private final CurrentAccount currentAccount;
    private final AuditLogService auditLogService;

    /** A unit with its class, as read once the class is locked. */
    public record LockedUnit(CoursePackage coursePackage, LearningModule unit) {
    }

    /** A lesson (never a working copy) with its class, as read once the class is locked. */
    public record LockedLesson(CoursePackage coursePackage, Lesson lesson) {
    }

    User requireAdmin(SecurityUserDetails currentUser) {
        return currentAccount.require(currentUser);
    }

    /** A lesson itself, never a working copy. */
    Lesson requireLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getRevisionOf() == null && lesson.getModule() != null)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.LESSON_NOT_FOUND));
    }

    /** The class, write-locked; the first read of a write on the class itself. */
    CoursePackage lockClass(Long classId) {
        return coursePackageRepository.findLockedById(classId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ClassContentSupport.CLASS_NOT_FOUND));
    }

    LockedUnit lockUnit(Long unitId) {
        Long classId = ClassContentSupport.requireOnly(packageItemRepository.findClassIdsOfUnit(unitId), UNIT_NOT_FOUND);
        CoursePackage locked = lockClass(classId);
        LearningModule unit = learningModuleRepository.findById(unitId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, UNIT_NOT_FOUND));
        return new LockedUnit(locked, unit);
    }

    void requireUnitOwner(Long unitId) {
        ClassContentSupport.requireOnly(packageItemRepository.findClassIdsOfUnit(unitId), UNIT_NOT_FOUND);
    }

    LockedLesson lockLesson(Long lessonId) {
        Long classId = ClassContentSupport.requireOnly(
                lessonRepository.findClassIdsOfLesson(lessonId), ClassContentSupport.LESSON_NOT_FOUND);
        CoursePackage locked = lockClass(classId);
        return new LockedLesson(locked, requireLesson(lessonId));
    }

    /** An archived class, its units and lessons included, is read-only until it is restored. */
    static void requireNotArchived(CoursePackage coursePackage) {
        if (coursePackage.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, CLASS_ARCHIVED);
        }
    }

    /** An audit entry about a class, named by its title. */
    void audit(User admin, String action, CoursePackage coursePackage, String description) {
        auditLogService.record(admin, action,
                new AuditLogService.AuditTarget(TARGET_CLASS, coursePackage.getId(), coursePackage.getTitle()), null,
                description);
    }

    /** An audit entry about a lesson, named by its title. */
    void audit(User admin, String action, Lesson lesson, String description) {
        auditLogService.record(admin, action, new AuditLogService.AuditTarget("Lesson", lesson.getId(), lesson.getTitle()),
                null, description);
    }
}
