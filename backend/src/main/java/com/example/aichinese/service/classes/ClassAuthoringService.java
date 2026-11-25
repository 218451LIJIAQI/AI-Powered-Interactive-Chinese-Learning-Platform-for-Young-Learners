package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningCategoryRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentSafetyService;

import lombok.RequiredArgsConstructor;

/**
 * Manages a class's details, units, lessons and publishing lifecycle. Edits to a published class's details are
 * immediately visible to learners. Any admin may edit a class.
 * Writes run on the class's lock ({@link ClassAuthoringAccess}). The audit log keeps what learners can notice: a new
 * class, its publishing, archiving, restoring and deleting, and edits of a published class; edits of a draft are not
 * audited.
 */
@Service
@RequiredArgsConstructor
public class ClassAuthoringService {

    public static final String ACTION_CLASS_CREATE = "ADMIN_CLASS_CREATE";
    public static final String ACTION_CLASS_PUBLISH = "ADMIN_CLASS_PUBLISH";
    public static final String ACTION_CLASS_ARCHIVE = "ADMIN_CLASS_ARCHIVE";

    /** The shape of a class (Library · UI rules, Classes): 2 units, 4 to 6 lessons. */
    static final int UNITS_PER_CLASS = 2;
    static final int MIN_LESSONS_TO_PUBLISH = 4;
    static final int MAX_LESSONS_TO_PUBLISH = 6;
    static final String UNITS_FULL = "A class has 2 units.";

    private final ClassAuthoringAccess access;
    private final ClassContentSupport content;
    private final LessonRevisionService revisions;
    private final LessonRevisionDiff revisionDiff;
    private final LessonEditorAssembler lessonEditorAssembler;
    private final LearningCategoryRepository learningCategoryRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final PackageItemRepository packageItemRepository;
    private final LessonRepository lessonRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final ContentSafetyService contentSafetyService;

    @Transactional
    public AdminClassViews.ClassPage createClass(SecurityUserDetails currentUser, ClassRequests.ClassDetails request) {
        User admin = access.requireAdmin(currentUser);
        CoursePackage coursePackage = new CoursePackage();
        applyDetails(coursePackage, request);
        coursePackage.setContentStatus(ContentStatus.DRAFT);
        CoursePackage saved = coursePackageRepository.save(coursePackage);
        access.audit(admin, ACTION_CLASS_CREATE, saved, "Admin created a class as a draft.");
        return page(saved);
    }

    @Transactional(readOnly = true)
    public AdminClassViews.ClassPage classPage(Long classId) {
        return page(content.requireClass(classId));
    }

    /**
     * Saves the details; they go live at once, also on a published class, whose edit is audited. An archived class is
     * read-only.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage saveDetails(
            SecurityUserDetails currentUser, Long classId, ClassRequests.ClassDetails request) {
        CoursePackage coursePackage = access.lockClass(classId);
        ClassAuthoringAccess.requireNotArchived(coursePackage);
        applyDetails(coursePackage, request);
        auditIfPublished(currentUser, coursePackage, "ADMIN_CLASS_EDIT", "Admin edited the details of a published class.");
        return page(coursePackage);
    }

    /** Publishes a draft class once it has its shape ({@link #canPublish}): it joins the catalogue. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage publish(SecurityUserDetails currentUser, Long classId) {
        User admin = access.requireAdmin(currentUser);
        CoursePackage coursePackage = access.lockClass(classId);
        if (coursePackage.getContentStatus() != ContentStatus.DRAFT) {
            throw new ResponseStatusException(CONFLICT, "Only a draft class can be published.");
        }
        List<LearningModule> units = content.units(coursePackage);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        if (!canPublish(coursePackage, lessonsByUnit.values().stream().flatMap(List::stream).toList())) {
            throw new ResponseStatusException(BAD_REQUEST, "To publish a class, give it 2 units with 4 to 6 published lessons.");
        }
        coursePackage.setContentStatus(ContentStatus.PUBLISHED);
        access.audit(admin, ACTION_CLASS_PUBLISH, coursePackage, "Admin published a class.");
        return page(coursePackage, units, lessonsByUnit);
    }

    /**
     * Archives a published class: it leaves the catalogue and nobody new can join it. Learners who joined keep its
     * lessons and their results (read-only). The class is read-only until it is restored.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage archive(SecurityUserDetails currentUser, Long classId) {
        User admin = access.requireAdmin(currentUser);
        CoursePackage coursePackage = access.lockClass(classId);
        if (coursePackage.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Class has already been archived.");
        }
        if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(CONFLICT, "Only a published class can be archived. Delete a draft class instead.");
        }
        coursePackage.setStatusBeforeArchive(coursePackage.getContentStatus());
        coursePackage.setContentStatus(ContentStatus.ARCHIVED);
        coursePackage.setArchivedAt(LocalDateTime.now());
        access.audit(admin, ACTION_CLASS_ARCHIVE, coursePackage, "Admin archived a class.");
        return page(coursePackage);
    }

    /** Restores an archived class to the status it had (a class archived before V3 may have been a draft). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage restore(SecurityUserDetails currentUser, Long classId) {
        User admin = access.requireAdmin(currentUser);
        CoursePackage coursePackage = access.lockClass(classId);
        if (coursePackage.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived class can be restored.");
        }
        ContentStatus before = coursePackage.getStatusBeforeArchive();
        coursePackage.setContentStatus(before == null ? ContentStatus.PUBLISHED : before);
        coursePackage.setStatusBeforeArchive(null);
        coursePackage.setArchivedAt(null);
        access.audit(admin, "ADMIN_CLASS_RESTORE", coursePackage, "Admin restored a class.");
        return page(coursePackage);
    }

    /** Deletes a draft class (never published, so no learner ever saw or joined it) with its units and lessons. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteClass(SecurityUserDetails currentUser, Long classId) {
        User admin = access.requireAdmin(currentUser);
        CoursePackage coursePackage = access.lockClass(classId);
        if (!canDelete(coursePackage)) {
            throw new ResponseStatusException(CONFLICT, "Only a draft class can be deleted.");
        }
        List<LearningModule> exclusiveUnits = learningModuleRepository.findAllById(
                packageItemRepository.findExclusiveItemIdsByClassId(classId));
        List<Lesson> lessons = content.lessonsByUnit(exclusiveUnits).values().stream().flatMap(List::stream).toList();
        if (!lessons.isEmpty()) {
            List<Lesson> workingCopies = lessonRepository.findByRevisionOfIdIn(
                    lessons.stream().map(Lesson::getId).toList());
            for (Lesson workingCopy : workingCopies) {
                revisions.deleteLessonTree(workingCopy);
            }
            for (Lesson lesson : lessons) {
                revisions.deleteLessonTree(lesson);
            }
        }
        packageItemRepository.deleteByCoursePackage(coursePackage);
        packageItemRepository.flush();
        learningModuleRepository.deleteAll(exclusiveUnits);
        coursePackageRepository.delete(coursePackage);
        access.audit(admin, "ADMIN_CLASS_DELETE", coursePackage, "Admin deleted a draft class.");
    }

    // Units and lessons

    /**
     * Adds a unit at the end of the class, up to its 2 units ({@link #UNITS_PER_CLASS}); a third is refused. On a published
     * class the new unit is audited.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage addUnit(
            SecurityUserDetails currentUser, Long classId, ClassRequests.UnitTitle request) {
        CoursePackage coursePackage = access.lockClass(classId);
        ClassAuthoringAccess.requireNotArchived(coursePackage);
        if (content.units(coursePackage).size() >= UNITS_PER_CLASS) {
            throw new ResponseStatusException(CONFLICT, UNITS_FULL);
        }
        LearningModule unit = new LearningModule();
        unit.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Unit title is required."));
        LearningModule saved = learningModuleRepository.save(unit);
        PackageItem item = new PackageItem();
        item.setCoursePackage(coursePackage);
        item.setModule(saved);
        item.setSortOrder(packageItemRepository.nextSortOrder(classId));
        packageItemRepository.save(item);
        touch(coursePackage);
        auditIfPublished(currentUser, coursePackage, "ADMIN_CLASS_UNIT_ADD",
                "Admin added the unit \"" + saved.getTitle() + "\" to a published class.");
        return page(coursePackage);
    }

    /** Renames a unit; on a published class learners see the new name at once, and the rename is audited. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.ClassPage renameUnit(
            SecurityUserDetails currentUser, Long unitId, ClassRequests.UnitTitle request) {
        ClassAuthoringAccess.LockedUnit locked = access.lockUnit(unitId);
        ClassAuthoringAccess.requireNotArchived(locked.coursePackage());
        String before = locked.unit().getTitle();
        locked.unit().setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Unit title is required."));
        touch(locked.coursePackage());
        auditIfPublished(currentUser, locked.coursePackage(), "ADMIN_CLASS_UNIT_RENAME",
                "Admin renamed the unit \"" + before + "\" of a published class to \"" + locked.unit().getTitle() + "\".");
        return page(locked.coursePackage());
    }

    /** Adds a draft lesson at the end of the unit and returns its editor view within the same write transaction. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor addLesson(Long unitId, ClassRequests.LessonTitle request) {
        ClassAuthoringAccess.LockedUnit locked = access.lockUnit(unitId);
        ClassAuthoringAccess.requireNotArchived(locked.coursePackage());
        Lesson lesson = new Lesson();
        lesson.setModule(locked.unit());
        lesson.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Lesson title is required."));
        lesson.setUnitPosition(content.nextLessonPosition(locked.unit()));
        lesson.setContentStatus(ContentStatus.DRAFT);
        Lesson saved = lessonRepository.saveAndFlush(lesson);
        touch(locked.coursePackage());
        return lessonEditorAssembler.view(locked.coursePackage(), saved, saved, false);
    }

    /** Audits an edit of a published class (learners see it at once); an edit of a draft class is not audited. */
    private void auditIfPublished(
            SecurityUserDetails currentUser, CoursePackage coursePackage, String action, String description) {
        if (coursePackage.getContentStatus() == ContentStatus.PUBLISHED) {
            access.audit(access.requireAdmin(currentUser), action, coursePackage, description);
        }
    }

    private AdminClassViews.ClassPage page(CoursePackage coursePackage) {
        List<LearningModule> units = content.units(coursePackage);
        return page(coursePackage, units, content.lessonsByUnit(units));
    }

    private AdminClassViews.ClassPage page(
            CoursePackage coursePackage, List<LearningModule> units, Map<Long, List<Lesson>> lessonsByUnit) {
        List<Lesson> lessons = lessonsByUnit.values().stream().flatMap(List::stream).toList();
        // As in the lesson editor: a working copy counts only while it differs in something learners would see (edits to
        // questions that learners have since locked can no longer be published).
        List<Lesson> workingCopies = lessons.isEmpty() ? List.of()
                : lessonRepository.findByRevisionOfIdIn(lessons.stream().map(Lesson::getId).toList());
        Set<Long> changedLessonIds = revisionDiff.changedLessonIds(workingCopies);
        List<AdminClassViews.Unit> unitViews = new ArrayList<>();
        for (LearningModule unit : units) {
            List<AdminClassViews.LessonRow> rows = lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                    .map(lesson -> new AdminClassViews.LessonRow(
                            lesson.getId(),
                            lesson.getTitle(),
                            lesson.getContentStatus().name(),
                            changedLessonIds.contains(lesson.getId())))
                    .toList();
            unitViews.add(new AdminClassViews.Unit(unit.getId(), unit.getTitle(), rows));
        }
        LearningCategory category = coursePackage.getCategory();
        return new AdminClassViews.ClassPage(
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                category == null ? null : new LearningCategoryResponse(category.getId(), category.getName()),
                coursePackage.getBestAfterUnit(),
                coursePackage.getContentStatus().name(),
                (int) lessons.stream().filter(lesson -> lesson.getContentStatus() != ContentStatus.ARCHIVED).count(),
                unitViews,
                canPublish(coursePackage, lessons));
    }

    /**
     * Whether the class can go live: a draft with the shape of a class (Library · UI rules, Classes), exactly 2 units
     * holding published lessons and 4 to 6 published lessons in all. A unit without a published lesson does not count
     * (learners never see it).
     */
    static boolean canPublish(CoursePackage coursePackage, List<Lesson> lessons) {
        if (coursePackage.getContentStatus() != ContentStatus.DRAFT) {
            return false;
        }
        List<Lesson> published = lessons.stream()
                .filter(lesson -> lesson.getContentStatus() == ContentStatus.PUBLISHED)
                .toList();
        long units = published.stream().map(lesson -> lesson.getModule().getId()).distinct().count();
        return units == UNITS_PER_CLASS
                && published.size() >= MIN_LESSONS_TO_PUBLISH
                && published.size() <= MAX_LESSONS_TO_PUBLISH;
    }

    /** A draft class: it was never published (a class is never unpublished), so no learner saw or joined it. */
    private boolean canDelete(CoursePackage coursePackage) {
        return coursePackage.getContentStatus() == ContentStatus.DRAFT
                && !enrolmentRepository.existsByCoursePackageId(coursePackage.getId());
    }

    private void applyDetails(CoursePackage coursePackage, ClassRequests.ClassDetails request) {
        coursePackage.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Class title is required."));
        coursePackage.setDescription(contentSafetyService.optionalSafeText(request.description(), 2000));
        coursePackage.setCategory(category(request.categoryId()));
        coursePackage.setBestAfterUnit(request.bestAfterUnit());
    }

    private LearningCategory category(Long categoryId) {
        return categoryId == null ? null : learningCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected learning category does not exist."));
    }

    /** A change to the class's units or lessons moves the class up the Classes list. */
    private void touch(CoursePackage coursePackage) {
        coursePackage.setUpdatedAt(LocalDateTime.now());
    }
}
