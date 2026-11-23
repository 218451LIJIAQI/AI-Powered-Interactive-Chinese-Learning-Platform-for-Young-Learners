package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.AdminClassViews;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.classes.QuizQuestionRequest;
import com.example.aichinese.dto.classes.QuizRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.learn.LearnerNotifications;

/**
 * The admin lesson editor: details and key phrases, the video, the practice activities and the quiz of a class lesson,
 * publishing, archiving and restoring it. A lesson is published only when it is complete: a video, key phrases, the 3
 * activities and a quiz of exactly 5 questions, none of them an unchecked AI draft ({@link LessonEditorAssembler}).
 * Edits of a published lesson go to its working copy (see {@link LessonRevisionService}): learners keep the published
 * lesson until the changes are published, and the changes can be discarded. Ids the editor sent while the lesson had no
 * working copy are mapped to the copies the first change creates. Every write runs on the class's lock
 * ({@link ClassAuthoringAccess}).
 */
@Service
public class LessonEditorService {

    /** A lesson quiz has exactly this many questions. */
    static final int QUIZ_QUESTIONS = 5;

    private static final String LESSON_ARCHIVED = "Restore the lesson before you edit it.";

    private final ClassAuthoringAccess access;
    private final ClassContentSupport content;
    private final LessonRevisionService revisions;
    private final LessonEditorAssembler assembler;
    private final LessonActivityEditor activityEditor;
    private final LessonQuizEditor quizEditor;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final FileStorageService fileStorageService;
    private final ContentSafetyService safety;
    private final LearnerNotifications learnerNotifications;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readOnlyTransaction;

    public LessonEditorService(
            ClassAuthoringAccess access,
            ClassContentSupport content,
            LessonRevisionService revisions,
            LessonEditorAssembler assembler,
            LessonActivityEditor activityEditor,
            LessonQuizEditor quizEditor,
            LessonKeyPhraseRepository keyPhraseRepository,
            RecordedLessonVideoRepository videoRepository,
            FileStorageService fileStorageService,
            ContentSafetyService safety,
            LearnerNotifications learnerNotifications,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.content = content;
        this.revisions = revisions;
        this.assembler = assembler;
        this.activityEditor = activityEditor;
        this.quizEditor = quizEditor;
        this.keyPhraseRepository = keyPhraseRepository;
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
        this.safety = safety;
        this.learnerNotifications = learnerNotifications;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /** The version shown in the editor, which can still be the live lesson until the first valid change. */
    private record EditTarget(Lesson lesson, CoursePackage coursePackage, Lesson version) {
        boolean needsWorkingCopy() {
            return version == lesson && lesson.getContentStatus() == ContentStatus.PUBLISHED;
        }
    }

    private ClassAuthoringAccess.LockedLesson lockLessonForAuthoring(Long lessonId) {
        ClassAuthoringAccess.LockedLesson locked = access.lockLesson(lessonId);
        ClassAuthoringAccess.requireNotArchived(locked.coursePackage());
        return locked;
    }

    private static void requireLessonEditable(Lesson lesson) {
        if (lesson.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, LESSON_ARCHIVED);
        }
    }

    private EditTarget editTarget(Long lessonId) {
        return editTarget(lockLessonForAuthoring(lessonId));
    }

    private EditTarget editTarget(ClassAuthoringAccess.LockedLesson locked) {
        Lesson lesson = locked.lesson();
        requireLessonEditable(lesson);
        Lesson version = lesson.getContentStatus() == ContentStatus.PUBLISHED
                ? content.revisionOf(lesson).orElse(lesson) : lesson;
        return new EditTarget(lesson, locked.coursePackage(), version);
    }

    /** Called after validation, while the enclosing class is still locked. */
    private EditTarget prepareWrite(EditTarget target) {
        return target.needsWorkingCopy()
                ? new EditTarget(target.lesson(), target.coursePackage(), revisions.createRevision(target.lesson()))
                : target;
    }

    @Transactional(readOnly = true)
    public AdminClassViews.LessonEditor editor(Long lessonId) {
        Lesson lesson = access.requireLesson(lessonId);
        return assembler.view(content.requireClassOfLesson(lesson), lesson);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor saveContent(
            SecurityUserDetails currentUser, Long lessonId, ClassRequests.LessonContent request) {
        EditTarget editable = editTarget(lessonId);
        Lesson lesson = editable.lesson();
        LearningModule targetUnit = resolveMoveTarget(editable.coursePackage(), lesson, request.unitId());
        String safeTitle = safety.requireSafeText(request.title(), 200, "Lesson title is required.");
        String safeSummary = safety.optionalSafeText(request.summary(), 1000);
        List<LessonViews.KeyPhrase> safePhrases = new ArrayList<>(request.keyPhrases().size());
        for (ClassRequests.KeyPhrase phrase : request.keyPhrases()) {
            safety.screenText(phrase.chinese(), phrase.pinyin(), phrase.meaning());
            safePhrases.add(new LessonViews.KeyPhrase(phrase.chinese(), phrase.pinyin(), phrase.meaning()));
        }
        if (editable.needsWorkingCopy()
                && Objects.equals(lesson.getTitle(), safeTitle)
                && Objects.equals(lesson.getSummary(), safeSummary)
                && Objects.equals(lesson.getEstimatedDurationMinutes(), request.minutes())
                && content.keyPhraseViews(lesson.getId()).equals(safePhrases)) {
            if (targetUnit != null) {
                moveLesson(currentUser, editable.coursePackage(), lesson, null, targetUnit);
            }
            return assembler.view(editable.coursePackage(), lesson, lesson);
        }

        EditTarget target = prepareWrite(editable);
        Lesson working = target.version();
        working.setTitle(safeTitle);
        working.setSummary(safeSummary);
        working.setEstimatedDurationMinutes(request.minutes());
        revisions.deleteKeyPhrases(working.getId());
        int order = 1;
        for (LessonViews.KeyPhrase phrase : safePhrases) {
            LessonKeyPhrase saved = new LessonKeyPhrase();
            saved.setLesson(working);
            saved.setChinese(phrase.chinese());
            saved.setPinyin(phrase.pinyin());
            saved.setMeaning(phrase.meaning());
            saved.setSortOrder(order++);
            keyPhraseRepository.save(saved);
        }
        if (targetUnit != null) {
            moveLesson(currentUser, target.coursePackage(), target.lesson(),
                    target.version() == target.lesson() ? null : target.version(), targetUnit);
        }
        return saved(target);
    }

    /** Validates a unit move before creating a working copy or mutating phrases. */
    private LearningModule resolveMoveTarget(CoursePackage coursePackage, Lesson lesson, Long unitId) {
        if (unitId == null || unitId.equals(lesson.getModule().getId())) {
            return null;
        }
        LearningModule unit = content.units(coursePackage).stream()
                .filter(candidate -> candidate.getId().equals(unitId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a unit of this class."));
        access.requireUnitOwner(unitId);
        return unit;
    }

    /** Moving a lesson to another unit of the same class is structure: it applies at once and is audited when live. */
    private void moveLesson(
            SecurityUserDetails currentUser,
            CoursePackage coursePackage,
            Lesson lesson,
            Lesson workingCopy,
            LearningModule unit) {
        int position = content.nextLessonPosition(unit);
        lesson.setModule(unit);
        lesson.setUnitPosition(position);
        // The working copy (when there is one) always follows its lesson.
        if (workingCopy != null) {
            workingCopy.setModule(unit);
            workingCopy.setUnitPosition(position);
        }
        if (coursePackage.getContentStatus() == ContentStatus.PUBLISHED
                && lesson.getContentStatus() == ContentStatus.PUBLISHED) {
            access.audit(access.requireAdmin(currentUser), "ADMIN_LESSON_EDIT", lesson,
                    "Admin moved a published lesson to \"" + unit.getTitle() + "\".");
        }
    }

    /**
     * Checks title and editability, then validates and streams the video to disk OUTSIDE the class write lock so a
     * large upload never stalls concurrent learners or editors, before linking the upload in a short write transaction.
     */
    public AdminClassViews.LessonEditor saveVideo(
            SecurityUserDetails currentUser,
            Long lessonId,
            String title,
            Integer durationSeconds,
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose a video file to upload.");
        }
        String safeTitle = safety.requireSafeText(title, 200, "Title is required.");
        readOnlyTransaction.executeWithoutResult(status -> {
            Lesson lesson = access.requireLesson(lessonId);
            ClassAuthoringAccess.requireNotArchived(content.requireClassOfLesson(lesson));
            requireLessonEditable(lesson);
        });
        try (var staged = fileStorageService.stageRecordedVideo(file)) {
            return writeTransaction.execute(status -> {
                EditTarget target = prepareWrite(editTarget(lessonId));
                User admin = access.requireAdmin(currentUser);
                UploadedFile uploadedFile = fileStorageService.saveStagedUpload(staged, admin);
                revisions.removeDraftVideos(target.version());
                RecordedLessonVideo video = new RecordedLessonVideo();
                video.setLesson(target.version());
                video.setSourceType(RecordedVideoSourceType.UPLOADED_FILE);
                video.setUploadedFile(uploadedFile);
                video.setTitle(safeTitle);
                video.setDurationSeconds(durationSeconds);
                video.setActive(true);
                videoRepository.save(video);
                return saved(target);
            });
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor removeVideo(Long lessonId) {
        EditTarget editable = editTarget(lessonId);
        if (editable.needsWorkingCopy() && content.video(editable.lesson().getId()).isEmpty()) {
            return assembler.view(editable.coursePackage(), editable.lesson(), editable.lesson(), false);
        }
        EditTarget target = prepareWrite(editable);
        revisions.removeDraftVideos(target.version());
        return saved(target);
    }

    /**
     * Fills one of the lesson's 3 activity slots: a lesson has exactly one Flashcards, one Tracing and one Matching
     * activity (the editor shows one slot per type), so a second activity of a type is refused; the slot's activity is
     * edited instead.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor addActivity(
            SecurityUserDetails currentUser,
            Long lessonId,
            ClassRequests.Activity request) {
        ClassActivityType type = LessonActivityEditor.parseType(request.type());
        EditTarget editable = editTarget(lessonId);
        activityEditor.requireSlotFree(editable.version(), type);
        String safeTitle = activityEditor.validate(type, request);
        User admin = access.requireAdmin(currentUser);
        EditTarget target = prepareWrite(editable);
        activityEditor.add(target.version(), admin, type, safeTitle, request);
        return saved(target);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor updateActivity(
            Long lessonId,
            String type,
            Long activityId,
            ClassRequests.Activity request) {
        ClassActivityType pathType = LessonActivityEditor.parseType(type);
        if (pathType != LessonActivityEditor.parseType(request.type())) {
            throw new ResponseStatusException(BAD_REQUEST, "The activity type can't be changed.");
        }
        EditTarget editable = editTarget(lessonId);
        LessonActivity existing = activityEditor.requireActivity(editable.version(), pathType, activityId);
        String safeTitle = activityEditor.validate(pathType, request);
        if (editable.needsWorkingCopy() && activityEditor.isUnchanged(existing, safeTitle, request)) {
            return assembler.view(editable.coursePackage(), editable.lesson(), editable.lesson(), false);
        }
        EditTarget target = prepareWrite(editable);
        LessonActivity toUpdate = editable.needsWorkingCopy()
                ? activityEditor.requireActivity(target.version(), pathType, activityId)
                : existing;
        activityEditor.update(toUpdate, safeTitle, request);
        return saved(target);
    }

    /** Creates the lesson's quiz; a lesson has one. "Allow SparkBot hints" is off unless the request turns it on. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor addQuiz(Long lessonId, QuizRequest request) {
        EditTarget editable = editTarget(lessonId);
        quizEditor.requireNoQuiz(editable.version());
        String safeTitle = quizEditor.validateQuiz(request);
        EditTarget target = prepareWrite(editable);
        quizEditor.addQuiz(target.version(), safeTitle, request);
        return saved(target);
    }

    /**
     * Turns "Allow SparkBot hints" on or off. Like every other edit of a published lesson it goes to the working copy:
     * learners keep the published setting until the changes are published. The questions stay as they are, so this is
     * possible after learners have taken the quiz too.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor updateQuiz(Long lessonId, Long quizId, ClassRequests.QuizSettings request) {
        EditTarget editable = editTarget(lessonId);
        Quiz existing = quizEditor.requireQuiz(editable.version(), quizId);
        if (editable.needsWorkingCopy() && existing.isHintsAllowed() == request.hintsAllowed()) {
            return assembler.view(editable.coursePackage(), editable.lesson(), editable.lesson(), false);
        }
        EditTarget target = prepareWrite(editable);
        Quiz toUpdate = editable.needsWorkingCopy() ? quizEditor.requireQuiz(target.version(), quizId) : existing;
        quizEditor.updateQuiz(toUpdate, request);
        return saved(target);
    }

    /**
     * Adds a question; a quiz holds at most {@link #QUIZ_QUESTIONS}. {@code position} (1-based, optional) puts it at
     * that place, so the Undo of a deletion brings a question back where it was; without one it goes last. A question
     * taken from the AI quiz draft ({@code aiDraft}) stays an unchecked AI draft until an admin keeps or edits it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor addQuestion(Long lessonId, Long quizId, QuizQuestionRequest request) {
        EditTarget editable = editTarget(lessonId);
        Quiz existing = quizEditor.requireCanAddQuestion(editable.version(), quizId);
        LessonQuizEditor.ValidatedQuestion validated = quizEditor.validateQuestion(request);
        EditTarget target = prepareWrite(editable);
        Quiz toUpdate = editable.needsWorkingCopy() ? quizEditor.requireQuiz(target.version(), quizId) : existing;
        quizEditor.addQuestion(toUpdate, request, validated);
        return saved(target);
    }

    /** Saves the question; an edit checks an AI draft question too. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor updateQuestion(Long lessonId, Long questionId, QuizQuestionRequest request) {
        EditTarget editable = editTarget(lessonId);
        QuizQuestion existing = quizEditor.requireCanEditQuestion(editable.lesson(), editable.version(), questionId);
        LessonQuizEditor.ValidatedQuestion validated = quizEditor.validateQuestion(request);
        if (editable.needsWorkingCopy() && quizEditor.isUnchanged(existing, validated)) {
            return assembler.view(editable.coursePackage(), editable.lesson(), editable.lesson(), false);
        }
        EditTarget target = prepareWrite(editable);
        QuizQuestion toUpdate = editable.needsWorkingCopy()
                ? quizEditor.resolveQuestionInRevision(target.lesson(), target.version(), questionId)
                : existing;
        quizEditor.updateQuestion(toUpdate, validated);
        return saved(target);
    }

    /** Keeps an AI draft question as it is: it is checked. Keeping a checked question changes nothing. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor keepQuestion(Long lessonId, Long questionId) {
        EditTarget editable = editTarget(lessonId);
        QuizQuestion existing = quizEditor.requireCanEditQuestion(editable.lesson(), editable.version(), questionId);
        if (editable.needsWorkingCopy() && !existing.isAiDraft()) {
            return assembler.view(editable.coursePackage(), editable.lesson(), editable.lesson(), false);
        }
        EditTarget target = prepareWrite(editable);
        QuizQuestion toKeep = editable.needsWorkingCopy()
                ? quizEditor.resolveQuestionInRevision(target.lesson(), target.version(), questionId)
                : existing;
        quizEditor.keepQuestion(toKeep);
        return saved(target);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor deleteQuestion(Long lessonId, Long questionId) {
        EditTarget editable = editTarget(lessonId);
        QuizQuestion existing = quizEditor.requireCanEditQuestion(editable.lesson(), editable.version(), questionId);
        EditTarget target = prepareWrite(editable);
        QuizQuestion toDelete = editable.needsWorkingCopy()
                ? quizEditor.resolveQuestionInRevision(target.lesson(), target.version(), questionId)
                : existing;
        quizEditor.deleteQuestion(toDelete);
        return saved(target);
    }

    /**
     * How many questions the AI quiz draft may write for the lesson's quiz: the places left of its
     * {@link #QUIZ_QUESTIONS} (all of them while the lesson has no quiz). Refused when the quiz is full or its
     * questions are locked, and while the lesson or its class is archived.
     */
    @Transactional(readOnly = true)
    public int openQuestionSlots(Long lessonId) {
        Lesson lesson = access.requireLesson(lessonId);
        ClassAuthoringAccess.requireNotArchived(content.requireClassOfLesson(lesson));
        requireLessonEditable(lesson);
        return quizEditor.openQuestionSlots(content.revisionOf(lesson).orElse(lesson));
    }

    /**
     * Publishes the lesson: a draft goes live with its parts ("Lesson published"), and the unpublished changes of a
     * published lesson replace what learners see ("Lesson edited"). Either way the version published must be complete
     * ({@link AdminClassViews.Checklist}). Saving a draft is not audited; publishing it is.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor publish(SecurityUserDetails currentUser, Long lessonId) {
        ClassAuthoringAccess.LockedLesson locked = lockLessonForAuthoring(lessonId);
        User admin = access.requireAdmin(currentUser);
        Lesson lesson = locked.lesson();
        switch (lesson.getContentStatus()) {
            case DRAFT -> {
                assembler.requireComplete(lesson);
                revisions.publishNewLesson(lesson);
                access.audit(admin, "ADMIN_LESSON_PUBLISH", lesson, "Admin published a lesson.");
            }
            case PUBLISHED -> {
                Lesson revision = content.revisionOf(lesson)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "There are no unpublished changes to publish."));
                assembler.requireComplete(revision);
                boolean changed = revisions.applyRevision(revision);
                if (changed) {
                    access.audit(admin, "ADMIN_LESSON_EDIT", lesson, "Admin published changes to a lesson.");
                    // The learners of a published class hear of it ("Lesson updated"); a draft class has none yet.
                    if (locked.coursePackage().getContentStatus() == ContentStatus.PUBLISHED) {
                        learnerNotifications.lessonUpdated(locked.coursePackage().getId(), locked.coursePackage().getTitle(),
                                lesson.getId(), lesson.getTitle());
                    }
                }
            }
            case ARCHIVED -> throw new ResponseStatusException(CONFLICT, LESSON_ARCHIVED);
        }
        return assembler.view(locked.coursePackage(), lesson, lesson);
    }

    /** Drops the unpublished changes of a published lesson; learners never saw them. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor discardRevision(Long lessonId) {
        ClassAuthoringAccess.LockedLesson locked = lockLessonForAuthoring(lessonId);
        Lesson lesson = locked.lesson();
        requireLessonEditable(lesson);
        Lesson revision = content.revisionOf(lesson)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "This lesson has no changes to discard."));
        revisions.deleteLessonTree(revision);
        return assembler.view(locked.coursePackage(), lesson, lesson);
    }

    /**
     * Archives the lesson: learners can't open it any more (their progress is kept), and it is read-only until it is
     * restored. Unpublished changes wait with it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor archive(SecurityUserDetails currentUser, Long lessonId) {
        ClassAuthoringAccess.LockedLesson locked = lockLessonForAuthoring(lessonId);
        User admin = access.requireAdmin(currentUser);
        Lesson lesson = locked.lesson();
        if (lesson.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "This lesson is already archived.");
        }
        lesson.setStatusBeforeArchive(lesson.getContentStatus());
        lesson.setContentStatus(ContentStatus.ARCHIVED);
        access.audit(admin, "ADMIN_LESSON_ARCHIVE", lesson, "Admin archived a lesson.");
        return assembler.view(locked.coursePackage(), lesson);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminClassViews.LessonEditor restore(SecurityUserDetails currentUser, Long lessonId) {
        ClassAuthoringAccess.LockedLesson locked = lockLessonForAuthoring(lessonId);
        User admin = access.requireAdmin(currentUser);
        Lesson lesson = locked.lesson();
        if (lesson.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived lesson can be restored.");
        }
        ContentStatus before = lesson.getStatusBeforeArchive();
        lesson.setContentStatus(before == null ? ContentStatus.DRAFT : before);
        lesson.setStatusBeforeArchive(null);
        access.audit(admin, "ADMIN_LESSON_RESTORE", lesson, "Admin restored a lesson.");
        return assembler.view(locked.coursePackage(), lesson);
    }

    /**
     * Marks the working copy as changed and answers with the editor. A working copy left with nothing to publish (only a
     * move to another unit, which applies at once, or an edit undone) is dropped.
     */
    private AdminClassViews.LessonEditor saved(EditTarget target) {
        Lesson working = target.version();
        boolean isRevision = working.getRevisionOf() != null;
        if (isRevision && !revisions.hasChanges(working)) {
            revisions.deleteLessonTree(working);
            return assembler.view(target.coursePackage(), target.lesson(), target.lesson(), false);
        }
        working.setUpdatedAt(LocalDateTime.now());
        return assembler.view(target.coursePackage(), target.lesson(), working, isRevision);
    }
}
