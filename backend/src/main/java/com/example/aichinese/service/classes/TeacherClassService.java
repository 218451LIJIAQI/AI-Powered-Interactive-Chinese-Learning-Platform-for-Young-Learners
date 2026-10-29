package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.dto.classes.TeacherClassViews.Change;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningCategory;
import com.example.aichinese.entity.LearningLevel;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.ClassRevisionStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningType;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.repository.ClassContentEventRepository;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.projection.IdCountProjection;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.TeacherContentAccessService;

/**
 * The teacher's classes: the class list, creating a class, its details (a pending revision once published), the
 * note to students, units and lessons (adding, renaming, reordering: structure goes live without review), archiving,
 * restoring and deleting a draft class.
 */
@Service
public class TeacherClassService {

    static final String CLASS_IN_REVIEW = "This class is in review. You can edit it again after the admin's decision.";
    static final String CLASS_ARCHIVED = "Restore the class before you edit it.";
    private static final String CLASS_AUDIT_TARGET = "CoursePackage";

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassEventService events;
    private final LessonRevisionService revisions;
    private final ClassLiveSessionService liveSessions;
    private final TeacherContentAccessService teacherContentAccessService;
    private final CoursePackageRepository coursePackageRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final PackageItemRepository packageItemRepository;
    private final LessonRepository lessonRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final ClassContentEventRepository eventRepository;
    private final ContentSafetyService contentSafetyService;
    private final AuditLogService auditLogService;

    public TeacherClassService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassEventService events,
            LessonRevisionService revisions,
            ClassLiveSessionService liveSessions,
            TeacherContentAccessService teacherContentAccessService,
            CoursePackageRepository coursePackageRepository,
            LearningModuleRepository learningModuleRepository,
            PackageItemRepository packageItemRepository,
            LessonRepository lessonRepository,
            ClassEnrolmentRepository enrolmentRepository,
            ClassContentEventRepository eventRepository,
            ContentSafetyService contentSafetyService,
            AuditLogService auditLogService) {
        this.access = access;
        this.content = content;
        this.events = events;
        this.revisions = revisions;
        this.liveSessions = liveSessions;
        this.teacherContentAccessService = teacherContentAccessService;
        this.coursePackageRepository = coursePackageRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.packageItemRepository = packageItemRepository;
        this.lessonRepository = lessonRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.eventRepository = eventRepository;
        this.contentSafetyService = contentSafetyService;
        this.auditLogService = auditLogService;
    }

    // ----- class list -----

    @Transactional(readOnly = true)
    public List<TeacherClassViews.ClassListItem> listClasses(SecurityUserDetails currentUser) {
        access.requireTeacher(currentUser);
        List<CoursePackage> classes = coursePackageRepository.findByTeacherUserIdOrderByUpdatedAtDesc(currentUser.getUserId());
        Map<Long, List<LearningModule>> units = content.unitsByClass(classes, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units.values().stream().flatMap(List::stream).toList());
        Map<Long, Lesson> revisionByLesson = content.revisionsByLesson(lessonsByUnit.values().stream().flatMap(List::stream).toList());
        Map<Long, Long> students = enrolmentRepository.countByClassIdIn(classes.stream().map(CoursePackage::getId).toList())
                .stream().collect(Collectors.toMap(IdCountProjection::getId, IdCountProjection::getTotal));
        List<TeacherClassViews.ClassListItem> items = new ArrayList<>();
        for (CoursePackage coursePackage : classes) {
            List<LearningModule> classUnits = units.getOrDefault(coursePackage.getId(), List.of());
            List<Lesson> lessons = classUnits.stream()
                    .flatMap(unit -> lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream())
                    .filter(lesson -> lesson.getContentStatus() != ContentStatus.ARCHIVED)
                    .toList();
            items.add(new TeacherClassViews.ClassListItem(
                    coursePackage.getId(),
                    coursePackage.getTitle(),
                    coursePackage.getContentStatus().name(),
                    coursePackage.getRevisionStatus() == null ? null : coursePackage.getRevisionStatus().name(),
                    levelName(coursePackage.getLevel()),
                    topicName(coursePackage.getCategory()),
                    coursePackage.getLearningType(),
                    classUnits.size(),
                    lessons.size(),
                    count(lessons, ContentStatus.PUBLISHED),
                    count(lessons, ContentStatus.SUBMITTED),
                    count(lessons, ContentStatus.DRAFT),
                    count(lessons, ContentStatus.REJECTED),
                    countRevisions(lessons, revisionByLesson, ContentStatus.SUBMITTED),
                    countRevisions(lessons, revisionByLesson, ContentStatus.REJECTED),
                    students.getOrDefault(coursePackage.getId(), 0L).intValue(),
                    coursePackage.getUpdatedAt()));
        }
        return items;
    }

    // ----- one class -----

    @Transactional
    public TeacherClassViews.ClassPage createClass(SecurityUserDetails currentUser, ClassRequests.ClassDetails request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setTeacher(teacher);
        applyDetails(coursePackage, request);
        coursePackage.setContentStatus(ContentStatus.DRAFT);
        CoursePackage saved = coursePackageRepository.save(coursePackage);
        events.record(saved, null, teacher.getUser(), ClassContentEventType.CLASS_CREATED);
        auditLogService.record(teacher.getUser(), "TEACHER_CLASS_CREATE",
                new AuditLogService.AuditTarget(CLASS_AUDIT_TARGET, saved.getId(), saved.getTitle()), null,
                "Teacher created a class.");
        return classPage(saved);
    }

    @Transactional(readOnly = true)
    public TeacherClassViews.ClassPage getClass(SecurityUserDetails currentUser, Long classId) {
        access.requireTeacher(currentUser);
        return classPage(access.requireOwnedClass(currentUser, classId));
    }

    /**
     * Saves the details: straight onto a class students have never seen, as a pending revision of a published one
     * (students keep the approved details until an admin approves the change).
     */
    @Transactional
    public TeacherClassViews.ClassPage saveDetails(
            SecurityUserDetails currentUser,
            Long classId,
            ClassRequests.ClassDetails request) {
        access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        switch (coursePackage.getContentStatus()) {
            case DRAFT, REJECTED -> applyDetails(coursePackage, request);
            case PUBLISHED -> {
                if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
                    throw new ResponseStatusException(CONFLICT, CLASS_IN_REVIEW);
                }
                applyPendingDetails(coursePackage, request);
                coursePackage.setRevisionStatus(ClassRevisionStatus.DRAFT);
            }
            case SUBMITTED -> throw new ResponseStatusException(CONFLICT, CLASS_IN_REVIEW);
            default -> throw new ResponseStatusException(CONFLICT, CLASS_ARCHIVED);
        }
        return classPage(coursePackageRepository.save(coursePackage));
    }

    /** Sends the pending details of a published class for review. A new class is reviewed with its first lesson. */
    @Transactional
    public TeacherClassViews.ClassPage submitDetails(SecurityUserDetails currentUser, Long classId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        if (coursePackage.getContentStatus() == ContentStatus.DRAFT || coursePackage.getContentStatus() == ContentStatus.REJECTED) {
            throw new ResponseStatusException(BAD_REQUEST, "A new class is reviewed together with the first lesson you submit.");
        }
        if (coursePackage.getContentStatus() != ContentStatus.PUBLISHED
                || coursePackage.getRevisionStatus() == null
                || coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
            throw new ResponseStatusException(BAD_REQUEST, "There are no saved changes to submit.");
        }
        List<Change> changes = detailChanges(coursePackage);
        if (changes.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "There are no saved changes to submit.");
        }
        coursePackage.setRevisionStatus(ClassRevisionStatus.SUBMITTED);
        coursePackage.setRevisionSubmittedAt(LocalDateTime.now());
        coursePackage.setRevisionRejectionReason(null);
        CoursePackage saved = coursePackageRepository.save(coursePackage);
        events.record(saved, null, teacher.getUser(), ClassContentEventType.CLASS_DETAILS_SUBMITTED, null, changes);
        return classPage(saved);
    }

    /** Drops the pending details; students never saw them. */
    @Transactional
    public TeacherClassViews.ClassPage discardDetails(SecurityUserDetails currentUser, Long classId) {
        access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        if (coursePackage.getRevisionStatus() == ClassRevisionStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, CLASS_IN_REVIEW);
        }
        clearPendingDetails(coursePackage);
        return classPage(coursePackageRepository.save(coursePackage));
    }

    /** The note to students goes live at once: it is only shown to students who joined the class. */
    @Transactional
    public TeacherClassViews.ClassPage saveNote(SecurityUserDetails currentUser, Long classId, ClassRequests.TeacherNote request) {
        access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        coursePackage.setTeacherNote(contentSafetyService.optionalSafeText(request.note(), 500));
        return classPage(coursePackageRepository.save(coursePackage));
    }

    /**
     * Archives the class: it leaves the catalogue and nobody new can join. Students who joined keep their progress
     * and can finish it; upcoming live sessions are cancelled and their booked students told.
     */
    @Transactional
    public TeacherClassViews.ClassPage archiveClass(SecurityUserDetails currentUser, Long classId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        archive(coursePackage, teacher.getUser());
        auditLogService.record(teacher.getUser(), "TEACHER_CLASS_ARCHIVE",
                new AuditLogService.AuditTarget(CLASS_AUDIT_TARGET, classId, coursePackage.getTitle()), null,
                "Teacher archived a class.");
        return classPage(coursePackage);
    }

    /** Shared by the teacher and the admin: archive with the status kept for a restore. */
    void archive(CoursePackage coursePackage, User actor) {
        if (coursePackage.getContentStatus() == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, CLASS_IN_REVIEW);
        }
        teacherContentAccessService.archiveOwnClass(coursePackage);
        coursePackageRepository.save(coursePackage);
        liveSessions.cancelUpcomingSessionsOfClass(coursePackage, actor);
        events.record(coursePackage, null, actor, ClassContentEventType.CLASS_ARCHIVED);
    }

    @Transactional
    public TeacherClassViews.ClassPage restoreClass(SecurityUserDetails currentUser, Long classId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        restore(coursePackage, teacher.getUser());
        auditLogService.record(teacher.getUser(), "TEACHER_CLASS_RESTORE",
                new AuditLogService.AuditTarget(CLASS_AUDIT_TARGET, classId, coursePackage.getTitle()), null,
                "Teacher restored a class.");
        return classPage(coursePackage);
    }

    void restore(CoursePackage coursePackage, User actor) {
        if (coursePackage.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived class can be restored.");
        }
        coursePackage.setContentStatus(ContentStatus.afterRestore(coursePackage.getStatusBeforeArchive()));
        coursePackage.setStatusBeforeArchive(null);
        coursePackageRepository.save(coursePackage);
        events.record(coursePackage, null, actor, ClassContentEventType.CLASS_RESTORED);
    }

    /** Deletes a class students never saw (a draft, or changes requested before it was ever approved). */
    @Transactional
    public void deleteClass(SecurityUserDetails currentUser, Long classId) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        if (!canDelete(coursePackage)) {
            throw new ResponseStatusException(BAD_REQUEST, "Only a draft class that students never saw can be deleted.");
        }
        eventRepository.deleteByClassId(classId);
        for (LearningModule unit : content.units(coursePackage, true)) {
            for (Lesson lesson : content.lessonsByUnit(List.of(unit)).getOrDefault(unit.getId(), List.of())) {
                content.revisionOf(lesson).ifPresent(revisions::deleteLessonTree);
                revisions.deleteLessonTree(lesson);
            }
        }
        List<PackageItem> items = packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(classId);
        List<Long> unitIds = items.stream()
                .filter(item -> item.getItemType() == PackageItemType.MODULE)
                .map(PackageItem::getItemId)
                .toList();
        packageItemRepository.deleteAll(items);
        packageItemRepository.flush();
        learningModuleRepository.deleteAllById(unitIds);
        coursePackageRepository.delete(coursePackage);
        auditLogService.record(teacher.getUser(), "TEACHER_CLASS_DELETE",
                new AuditLogService.AuditTarget(CLASS_AUDIT_TARGET, classId, coursePackage.getTitle()), null,
                "Teacher deleted a draft class.");
    }

    @Transactional(readOnly = true)
    public List<TeacherClassViews.Event> history(SecurityUserDetails currentUser, Long classId) {
        access.requireTeacher(currentUser);
        access.requireOwnedClass(currentUser, classId);
        return events.classHistory(classId);
    }

    // ----- units and lessons (structure goes live without review) -----

    @Transactional
    public TeacherClassViews.ClassPage addUnit(SecurityUserDetails currentUser, Long classId, ClassRequests.UnitTitle request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        requireStructureEditable(coursePackage);
        LearningModule unit = new LearningModule();
        unit.setTeacher(teacher);
        unit.setLevel(coursePackage.getLevel());
        unit.setCategory(coursePackage.getCategory());
        unit.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Unit title is required."));
        unit.setContentStatus(ContentStatus.PUBLISHED);
        LearningModule saved = learningModuleRepository.save(unit);
        List<PackageItem> items = packageItemRepository.findByCoursePackageIdAndItemTypeOrderBySortOrderAscIdAsc(
                classId, PackageItemType.MODULE);
        PackageItem item = new PackageItem();
        item.setCoursePackage(coursePackage);
        item.setItemType(PackageItemType.MODULE);
        item.setItemId(saved.getId());
        item.setSortOrder(items.stream().mapToInt(PackageItem::getSortOrder).max().orElse(0) + 1);
        packageItemRepository.save(item);
        touch(coursePackage);
        return classPage(coursePackage);
    }

    @Transactional
    public TeacherClassViews.ClassPage renameUnit(SecurityUserDetails currentUser, Long unitId, ClassRequests.UnitTitle request) {
        access.requireTeacher(currentUser);
        ClassAccessService.OwnedUnit owned = access.requireOwnedUnit(currentUser, unitId);
        requireStructureEditable(owned.coursePackage());
        owned.unit().setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Unit title is required."));
        learningModuleRepository.save(owned.unit());
        touch(owned.coursePackage());
        return classPage(owned.coursePackage());
    }

    /** A unit can be archived once every lesson in it is archived (or it has none). */
    @Transactional
    public TeacherClassViews.ClassPage archiveUnit(SecurityUserDetails currentUser, Long unitId) {
        access.requireTeacher(currentUser);
        ClassAccessService.OwnedUnit owned = access.requireOwnedUnit(currentUser, unitId);
        requireStructureEditable(owned.coursePackage());
        boolean hasLessons = content.lessonsByUnit(List.of(owned.unit())).getOrDefault(unitId, List.of()).stream()
                .anyMatch(lesson -> lesson.getContentStatus() != ContentStatus.ARCHIVED);
        if (hasLessons) {
            throw new ResponseStatusException(BAD_REQUEST, "Archive or move the lessons of this unit first.");
        }
        owned.unit().setStatusBeforeArchive(ContentStatus.PUBLISHED);
        owned.unit().setContentStatus(ContentStatus.ARCHIVED);
        learningModuleRepository.save(owned.unit());
        touch(owned.coursePackage());
        return classPage(owned.coursePackage());
    }

    /** Applies a new order of units and lessons; every active unit and lesson must be listed exactly once. */
    @Transactional
    public TeacherClassViews.ClassPage reorder(SecurityUserDetails currentUser, Long classId, ClassRequests.Structure request) {
        access.requireTeacher(currentUser);
        CoursePackage coursePackage = access.requireOwnedClass(currentUser, classId);
        requireStructureEditable(coursePackage);
        List<LearningModule> units = content.units(coursePackage, false);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        Set<Long> unitIds = units.stream().map(LearningModule::getId).collect(Collectors.toSet());
        Map<Long, Lesson> lessons = new HashMap<>();
        lessonsByUnit.values().forEach(list -> list.forEach(lesson -> lessons.put(lesson.getId(), lesson)));
        Set<Long> requestedUnits = new HashSet<>();
        Set<Long> requestedLessons = new HashSet<>();
        for (ClassRequests.UnitOrder unitOrder : request.units()) {
            requestedUnits.add(unitOrder.unitId());
            requestedLessons.addAll(unitOrder.lessonIds());
        }
        if (!requestedUnits.equals(unitIds) || !requestedLessons.equals(lessons.keySet())
                || requestedLessons.size() != request.units().stream().mapToInt(order -> order.lessonIds().size()).sum()) {
            throw new ResponseStatusException(BAD_REQUEST, "The new order must list every unit and lesson of the class once.");
        }
        Map<Long, PackageItem> itemByUnit = packageItemRepository
                .findByCoursePackageIdAndItemTypeOrderBySortOrderAscIdAsc(classId, PackageItemType.MODULE).stream()
                .collect(Collectors.toMap(PackageItem::getItemId, item -> item, (first, second) -> first));
        Map<Long, LearningModule> unitById = units.stream().collect(Collectors.toMap(LearningModule::getId, unit -> unit));
        int unitPosition = 1;
        for (ClassRequests.UnitOrder unitOrder : request.units()) {
            PackageItem item = itemByUnit.get(unitOrder.unitId());
            item.setSortOrder(unitPosition++);
            packageItemRepository.save(item);
            int lessonPosition = 1;
            for (Long lessonId : unitOrder.lessonIds()) {
                Lesson lesson = lessons.get(lessonId);
                moveLesson(lesson, unitById.get(unitOrder.unitId()), lessonPosition++);
            }
        }
        touch(coursePackage);
        return classPage(coursePackage);
    }

    /** Moves a lesson (and its revision copy, which always follows it) to a unit and position. */
    void moveLesson(Lesson lesson, LearningModule unit, int position) {
        lesson.setModule(unit);
        lesson.setUnitPosition(position);
        lessonRepository.save(lesson);
        content.revisionOf(lesson).ifPresent(revision -> {
            revision.setModule(unit);
            revision.setUnitPosition(position);
            lessonRepository.save(revision);
        });
    }

    @Transactional
    public Long addLesson(SecurityUserDetails currentUser, Long unitId, ClassRequests.LessonTitle request) {
        TeacherProfile teacher = access.requireTeacher(currentUser);
        ClassAccessService.OwnedUnit owned = access.requireOwnedUnit(currentUser, unitId);
        requireStructureEditable(owned.coursePackage());
        if (owned.unit().getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "This unit is archived.");
        }
        List<Lesson> existing = content.lessonsByUnit(List.of(owned.unit())).getOrDefault(unitId, List.of());
        Lesson lesson = new Lesson();
        lesson.setTeacher(teacher);
        lesson.setModule(owned.unit());
        lesson.setLevel(owned.coursePackage().getLevel());
        lesson.setCategory(owned.coursePackage().getCategory());
        lesson.setLearningType(LearningType.RECORDED);
        lesson.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Lesson title is required."));
        lesson.setUnitPosition(existing.stream().mapToInt(Lesson::getUnitPosition).max().orElse(0) + 1);
        lesson.setContentStatus(ContentStatus.DRAFT);
        Lesson saved = lessonRepository.save(lesson);
        events.record(owned.coursePackage(), saved, teacher.getUser(), ClassContentEventType.LESSON_CREATED);
        touch(owned.coursePackage());
        return saved.getId();
    }

    // ----- views and helpers -----

    TeacherClassViews.ClassPage classPage(CoursePackage coursePackage) {
        List<LearningModule> units = content.units(coursePackage, true);
        Map<Long, List<Lesson>> lessonsByUnit = content.lessonsByUnit(units);
        List<Lesson> lessons = lessonsByUnit.values().stream().flatMap(List::stream).toList();
        Map<Long, Lesson> revisionByLesson = content.revisionsByLesson(lessons);
        List<Long> shownIds = lessons.stream()
                .map(lesson -> revisionByLesson.getOrDefault(lesson.getId(), lesson).getId())
                .toList();
        Map<Long, List<LessonActivity>> activities = content.activitiesByLesson(shownIds);
        Map<Long, List<Quiz>> quizzes = content.quizzesByLesson(shownIds);
        Map<Long, List<QuizQuestion>> questions = content.questionsByQuiz(
                quizzes.values().stream().flatMap(List::stream).filter(ClassContentSupport::isActive).map(Quiz::getId).toList());
        // The newest "changes requested" step with a note per lesson: who wrote its open review note.
        Map<Long, TeacherClassViews.Event> noteEvents = new HashMap<>();
        for (TeacherClassViews.Event event : events.classHistory(coursePackage.getId())) {
            if (event.lessonId() != null && event.note() != null && event.type().endsWith("CHANGES_REQUESTED")) {
                noteEvents.putIfAbsent(event.lessonId(), event);
            }
        }
        List<TeacherClassViews.Unit> unitViews = new ArrayList<>();
        for (LearningModule unit : units) {
            boolean unitArchived = unit.getContentStatus() == ContentStatus.ARCHIVED;
            List<Lesson> unitLessons = lessonsByUnit.getOrDefault(unit.getId(), List.of());
            if (unitArchived && unitLessons.isEmpty()) {
                continue;
            }
            List<TeacherClassViews.LessonRow> rows = new ArrayList<>();
            for (Lesson lesson : unitLessons) {
                Lesson shown = revisionByLesson.getOrDefault(lesson.getId(), lesson);
                Lesson revision = revisionByLesson.get(lesson.getId());
                int activityCount = (int) activities.getOrDefault(shown.getId(), List.of()).stream()
                        .filter(ClassContentSupport::isActive).count();
                int questionCount = quizzes.getOrDefault(shown.getId(), List.of()).stream()
                        .filter(ClassContentSupport::isActive)
                        .mapToInt(quiz -> questions.getOrDefault(quiz.getId(), List.of()).size())
                        .sum();
                String reviewNote = revision != null ? revision.getRejectionReason() : lesson.getRejectionReason();
                TeacherClassViews.Event noteEvent = reviewNote == null ? null : noteEvents.get(lesson.getId());
                rows.add(new TeacherClassViews.LessonRow(
                        lesson.getId(),
                        shown.getTitle(),
                        lesson.getContentStatus().name(),
                        revision == null ? null : revisionStatusName(revision.getContentStatus()),
                        reviewNote,
                        noteEvent == null ? null : noteEvent.actorName(),
                        activityCount,
                        questionCount,
                        ClassContentSupport.latest(lesson.getUpdatedAt(), revision == null ? null : revision.getUpdatedAt())));
            }
            unitViews.add(new TeacherClassViews.Unit(unit.getId(), unit.getTitle(), unitArchived, rows));
        }
        return new TeacherClassViews.ClassPage(
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getDescription(),
                coursePackage.getTeacherNote(),
                coursePackage.getLevel() == null ? null : coursePackage.getLevel().getId(),
                levelName(coursePackage.getLevel()),
                coursePackage.getCategory() == null ? null : coursePackage.getCategory().getId(),
                topicName(coursePackage.getCategory()),
                coursePackage.getLearningType(),
                coursePackage.getContentStatus().name(),
                coursePackage.getRejectionReason(),
                detailsRevision(coursePackage),
                unitViews,
                (int) enrolmentRepository.countByCoursePackageId(coursePackage.getId()),
                canDelete(coursePackage));
    }

    private static TeacherClassViews.DetailsRevision detailsRevision(CoursePackage coursePackage) {
        if (coursePackage.getRevisionStatus() == null) {
            return null;
        }
        return new TeacherClassViews.DetailsRevision(
                coursePackage.getRevisionStatus().name(),
                coursePackage.getPendingTitle(),
                coursePackage.getPendingDescription(),
                coursePackage.getPendingLevel() == null ? null : coursePackage.getPendingLevel().getId(),
                coursePackage.getPendingCategory() == null ? null : coursePackage.getPendingCategory().getId(),
                coursePackage.getPendingLearningType(),
                coursePackage.getRevisionRejectionReason());
    }

    private void applyDetails(CoursePackage coursePackage, ClassRequests.ClassDetails request) {
        coursePackage.setTitle(contentSafetyService.requireSafeText(request.title(), 200, "Class title is required."));
        coursePackage.setDescription(contentSafetyService.optionalSafeText(request.description(), 2000));
        coursePackage.setLevel(request.levelId() == null ? null : teacherContentAccessService.requireLevel(request.levelId()));
        coursePackage.setCategory(request.topicId() == null ? null : teacherContentAccessService.requireCategory(request.topicId()));
        coursePackage.setLearningType(request.learningType() == null ? LearningType.HYBRID : request.learningType());
    }

    private void applyPendingDetails(CoursePackage coursePackage, ClassRequests.ClassDetails request) {
        coursePackage.setPendingTitle(contentSafetyService.requireSafeText(request.title(), 200, "Class title is required."));
        coursePackage.setPendingDescription(contentSafetyService.optionalSafeText(request.description(), 2000));
        coursePackage.setPendingLevel(request.levelId() == null ? null : teacherContentAccessService.requireLevel(request.levelId()));
        coursePackage.setPendingCategory(request.topicId() == null ? null : teacherContentAccessService.requireCategory(request.topicId()));
        coursePackage.setPendingLearningType(request.learningType() == null ? LearningType.HYBRID : request.learningType());
    }

    private static void clearPendingDetails(CoursePackage coursePackage) {
        coursePackage.setPendingTitle(null);
        coursePackage.setPendingDescription(null);
        coursePackage.setPendingLevel(null);
        coursePackage.setPendingCategory(null);
        coursePackage.setPendingLearningType(null);
        coursePackage.setRevisionStatus(null);
        coursePackage.setRevisionRejectionReason(null);
        coursePackage.setRevisionSubmittedAt(null);
    }

    /** What the pending details change (for the admin review and the history). */
    static List<Change> detailChanges(CoursePackage coursePackage) {
        List<Change> changes = new ArrayList<>();
        if (!Objects.equals(coursePackage.getTitle(), coursePackage.getPendingTitle())) {
            changes.add(new Change("TITLE", coursePackage.getPendingTitle(), null, null));
        }
        if (!Objects.equals(coursePackage.getDescription(), coursePackage.getPendingDescription())) {
            changes.add(new Change("DESCRIPTION", null, null, null));
        }
        if (!Objects.equals(id(coursePackage.getLevel()), id(coursePackage.getPendingLevel()))) {
            changes.add(new Change("LEVEL", levelName(coursePackage.getPendingLevel()), null, null));
        }
        if (!Objects.equals(id(coursePackage.getCategory()), id(coursePackage.getPendingCategory()))) {
            changes.add(new Change("TOPIC", topicName(coursePackage.getPendingCategory()), null, null));
        }
        if (coursePackage.getLearningType() != coursePackage.getPendingLearningType()) {
            changes.add(new Change("LEARNING_TYPE", null, null, null));
        }
        return changes;
    }

    /** Applies approved pending details to the class. */
    static void applyApprovedDetails(CoursePackage coursePackage) {
        coursePackage.setTitle(coursePackage.getPendingTitle());
        coursePackage.setDescription(coursePackage.getPendingDescription());
        coursePackage.setLevel(coursePackage.getPendingLevel());
        coursePackage.setCategory(coursePackage.getPendingCategory());
        coursePackage.setLearningType(coursePackage.getPendingLearningType() == null
                ? coursePackage.getLearningType()
                : coursePackage.getPendingLearningType());
        clearPendingDetails(coursePackage);
    }

    private static Long id(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    private static void requireStructureEditable(CoursePackage coursePackage) {
        if (coursePackage.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, CLASS_ARCHIVED);
        }
    }

    private void touch(CoursePackage coursePackage) {
        coursePackage.setUpdatedAt(LocalDateTime.now());
        coursePackageRepository.save(coursePackage);
    }

    private boolean canDelete(CoursePackage coursePackage) {
        ContentStatus status = coursePackage.getContentStatus();
        return (status == ContentStatus.DRAFT || status == ContentStatus.REJECTED)
                && enrolmentRepository.countByCoursePackageId(coursePackage.getId()) == 0;
    }

    private static int count(List<Lesson> lessons, ContentStatus status) {
        return (int) lessons.stream().filter(lesson -> lesson.getContentStatus() == status).count();
    }

    private static int countRevisions(List<Lesson> lessons, Map<Long, Lesson> revisions, ContentStatus status) {
        return (int) lessons.stream()
                .map(lesson -> revisions.get(lesson.getId()))
                .filter(revision -> revision != null && revision.getContentStatus() == status)
                .count();
    }

    static String revisionStatusName(ContentStatus status) {
        return switch (status) {
            case SUBMITTED -> ClassRevisionStatus.SUBMITTED.name();
            case REJECTED -> ClassRevisionStatus.REJECTED.name();
            default -> ClassRevisionStatus.DRAFT.name();
        };
    }

    static String levelName(LearningLevel level) {
        return level == null ? null : level.getName();
    }

    static String topicName(LearningCategory category) {
        return category == null ? null : category.getName();
    }
}
