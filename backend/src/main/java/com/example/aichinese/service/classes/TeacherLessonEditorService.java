package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.TeacherClassViews;
import com.example.aichinese.dto.learning.QuizOptionRequest;
import com.example.aichinese.dto.learning.QuizQuestionRequest;
import com.example.aichinese.dto.learning.QuizRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ClassContentEventType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.repository.ClassEnrolmentRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.LiveLearningAccessService;

/**
 * The lesson editor: details and key phrases, the video, the practice activities and the quizzes of a class lesson,
 * submitting it for review, archiving and restoring. Edits of a published lesson go to its revision (see
 * {@link LessonRevisionService}); a lesson in review is read-only. Ids the editor sent while the lesson had no
 * revision yet are mapped to the copies the first change creates.
 */
@Service
public class TeacherLessonEditorService {

    private static final String QUESTIONS_LOCKED = "Quiz questions cannot be changed after students have submitted attempts.";
    private static final String ACTIVITY_NOT_FOUND = "Activity was not found.";
    private static final String QUIZ_NOT_FOUND = "Requested quiz was not found.";
    private static final String QUESTION_NOT_FOUND = "Requested quiz question was not found.";
    private static final int MIN_QUIZ_QUESTIONS = 3;

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassEventService events;
    private final LessonRevisionService revisions;
    private final TeacherClassService classes;
    private final LessonRepository lessonRepository;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final ClassEnrolmentRepository enrolmentRepository;
    private final FileStorageService fileStorageService;
    private final LiveLearningAccessService liveRules;
    private final ContentSafetyService safety;

    public TeacherLessonEditorService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassEventService events,
            LessonRevisionService revisions,
            TeacherClassService classes,
            LessonRepository lessonRepository,
            LessonKeyPhraseRepository keyPhraseRepository,
            RecordedLessonVideoRepository videoRepository,
            VideoProgressRepository videoProgressRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            CoursePackageRepository coursePackageRepository,
            ClassEnrolmentRepository enrolmentRepository,
            FileStorageService fileStorageService,
            LiveLearningAccessService liveRules,
            ContentSafetyService safety) {
        this.access = access;
        this.content = content;
        this.events = events;
        this.revisions = revisions;
        this.classes = classes;
        this.lessonRepository = lessonRepository;
        this.keyPhraseRepository = keyPhraseRepository;
        this.videoRepository = videoRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.coursePackageRepository = coursePackageRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.fileStorageService = fileStorageService;
        this.liveRules = liveRules;
        this.safety = safety;
    }

    /** The lesson the request names, its class, and the copy edits go to. */
    private record Target(Lesson lesson, CoursePackage coursePackage, Lesson working) {
    }

    private Target edit(SecurityUserDetails currentUser, Long lessonId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        if (coursePackage.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(CONFLICT, TeacherClassService.CLASS_ARCHIVED);
        }
        return new Target(lesson, coursePackage, revisions.workingCopy(lesson));
    }

    // ----- read -----

    @Transactional(readOnly = true)
    public TeacherClassViews.LessonEditor editor(SecurityUserDetails currentUser, Long lessonId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        return view(lesson);
    }

    // ----- content -----

    @Transactional
    public TeacherClassViews.LessonEditor saveContent(
            SecurityUserDetails currentUser,
            Long lessonId,
            ClassRequests.LessonContent request) {
        Target target = edit(currentUser, lessonId);
        Lesson working = target.working();
        working.setTitle(safety.requireSafeText(request.title(), 200, "Lesson title is required."));
        working.setSummary(safety.optionalSafeText(request.summary(), 1000));
        working.setEstimatedDurationMinutes(request.minutes());
        lessonRepository.save(working);
        revisions.deleteKeyPhrases(working.getId());
        int order = 1;
        for (ClassRequests.KeyPhrase phrase : request.keyPhrases()) {
            safety.screenText(phrase.chinese(), phrase.pinyin(), phrase.meaning());
            LessonKeyPhrase saved = new LessonKeyPhrase();
            saved.setLesson(working);
            saved.setChinese(phrase.chinese());
            saved.setPinyin(phrase.pinyin());
            saved.setMeaning(phrase.meaning());
            saved.setSortOrder(order++);
            keyPhraseRepository.save(saved);
        }
        if (request.unitId() != null && !request.unitId().equals(target.lesson().getModule().getId())) {
            moveToUnit(target, request.unitId());
        }
        touch(target);
        return view(target.lesson());
    }

    /** Moving a lesson to another unit of the same class is structure: it goes live without review. */
    private void moveToUnit(Target target, Long unitId) {
        LearningModule unit = content.units(target.coursePackage(), false).stream()
                .filter(candidate -> candidate.getId().equals(unitId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a unit of this class."));
        int position = content.lessonsByUnit(List.of(unit)).getOrDefault(unitId, List.of()).stream()
                .mapToInt(Lesson::getUnitPosition).max().orElse(0) + 1;
        classes.moveLesson(target.lesson(), unit, position);
    }

    /** Replaces the lesson video with an uploaded file or a link. */
    @Transactional
    public TeacherClassViews.LessonEditor saveVideo(
            SecurityUserDetails currentUser,
            Long lessonId,
            String title,
            String sourceType,
            String externalVideoUrl,
            Integer durationSeconds,
            MultipartFile file) {
        Target target = edit(currentUser, lessonId);
        RecordedVideoSourceType type = liveRules.parseRecordedVideoSourceType(sourceType);
        UploadedFile uploadedFile = null;
        if (type == RecordedVideoSourceType.UPLOADED_FILE) {
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Choose a video file to upload.");
            }
            uploadedFile = fileStorageService.storeRecordedVideo(file, target.lesson().getTeacher().getUser());
        }
        String link = liveRules.validateRecordedVideoSource(type, externalVideoUrl, uploadedFile);
        for (RecordedLessonVideo old : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(target.working().getId())) {
            old.setActive(false);
            videoRepository.save(old);
        }
        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setLesson(target.working());
        video.setSourceType(type);
        video.setUploadedFile(uploadedFile);
        video.setExternalVideoUrl(link);
        video.setTitle(safety.requireSafeText(title, 200, "Title is required."));
        video.setDurationSeconds(durationSeconds == null || durationSeconds < 0 ? null : durationSeconds);
        video.setActive(true);
        videoRepository.save(video);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor removeVideo(SecurityUserDetails currentUser, Long lessonId) {
        Target target = edit(currentUser, lessonId);
        for (RecordedLessonVideo video : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(target.working().getId())) {
            video.setActive(false);
            videoRepository.save(video);
        }
        touch(target);
        return view(target.lesson());
    }

    // ----- activities -----

    @Transactional
    public TeacherClassViews.LessonEditor addActivity(
            SecurityUserDetails currentUser,
            Long lessonId,
            ClassRequests.Activity request) {
        Target target = edit(currentUser, lessonId);
        ClassActivityType type = parseType(request.type());
        LessonActivity activity = switch (type) {
            case FLASHCARDS -> new Flashcard();
            case TRACING -> new TracingExercise();
            case MATCHING -> new MatchingGame();
        };
        ((PracticeActivityContent) activity).setCreatedByUser(target.lesson().getTeacher().getUser());
        ((PracticeActivityContent) activity).setContentStatus(ContentStatus.DRAFT);
        activity.setLesson(target.working());
        activity.setLessonPosition(nextPosition(target.working()));
        applyActivity(activity, request);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor updateActivity(
            SecurityUserDetails currentUser,
            Long lessonId,
            String type,
            Long activityId,
            ClassRequests.Activity request) {
        Target target = edit(currentUser, lessonId);
        LessonActivity activity = resolveActivity(target, parseType(type), activityId);
        if (ClassContentSupport.typeOf(activity) != parseType(request.type())) {
            throw new ResponseStatusException(BAD_REQUEST, "The activity type can't be changed.");
        }
        applyActivity(activity, request);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor deleteActivity(SecurityUserDetails currentUser, Long lessonId, String type, Long activityId) {
        Target target = edit(currentUser, lessonId);
        revisions.deleteActivity(resolveActivity(target, parseType(type), activityId));
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor duplicateActivity(SecurityUserDetails currentUser, Long lessonId, String type, Long activityId) {
        Target target = edit(currentUser, lessonId);
        LessonActivity source = resolveActivity(target, parseType(type), activityId);
        LessonActivity copy = revisions.duplicateActivity(source, target.working(), nextPosition(target.working()));
        ((PracticeActivityContent) copy).setTitle(copyTitle(source.getTitle(), 200));
        revisions.saveActivity(copy);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor reorderActivities(
            SecurityUserDetails currentUser,
            Long lessonId,
            ClassRequests.ActivityOrder request) {
        Target target = edit(currentUser, lessonId);
        List<LessonActivity> activities = content.activities(target.working().getId()).stream()
                .filter(ClassContentSupport::isActive)
                .toList();
        Map<String, LessonActivity> byKey = new HashMap<>();
        activities.forEach(activity -> byKey.put(ClassContentSupport.typeOf(activity) + "#" + activity.getId(), activity));
        List<LessonActivity> ordered = new ArrayList<>();
        for (ClassRequests.ActivityOrderItem item : request.activities()) {
            LessonActivity activity = byKey.remove(parseType(item.type()) + "#" + item.id());
            if (activity == null) {
                activity = byKey.remove(parseType(item.type()) + "#" + mappedActivityId(target, parseType(item.type()), item.id()));
            }
            if (activity == null) {
                throw new ResponseStatusException(BAD_REQUEST, "The new order must list every activity of the lesson once.");
            }
            ordered.add(activity);
        }
        if (!byKey.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "The new order must list every activity of the lesson once.");
        }
        int position = 1;
        for (LessonActivity activity : ordered) {
            activity.setLessonPosition(position++);
            revisions.saveActivity(activity);
        }
        touch(target);
        return view(target.lesson());
    }

    private void applyActivity(LessonActivity activity, ClassRequests.Activity request) {
        PracticeActivityContent entity = (PracticeActivityContent) activity;
        entity.setTitle(safety.requireSafeText(request.title(), 200, "Activity title is required."));
        entity.setLearningStage(request.stage());
        entity.setTopic(null);
        if (activity instanceof Flashcard card) {
            // Lesson flashcards review the lesson's key phrases; the card fields only keep their required values.
            card.setFrontText(entity.getTitle());
            card.setBackText("");
            revisions.saveActivity(card);
        } else if (activity instanceof TracingExercise tracing) {
            if (request.characters().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Add at least one character to trace.");
            }
            for (ClassRequests.TraceCharacter character : request.characters()) {
                if (!TracingCharacters.isSingleCharacter(character.character())) {
                    throw new ResponseStatusException(BAD_REQUEST, "Enter one character per row.");
                }
                safety.screenText(character.character(), character.pinyin(), character.meaning());
            }
            TracingCharacters.write(tracing, request.characters());
            revisions.saveActivity(tracing);
        } else if (activity instanceof MatchingGame game) {
            if (request.pairs().size() < 2) {
                throw new ResponseStatusException(BAD_REQUEST, "A matching activity needs at least 2 pairs.");
            }
            Set<String> meanings = new HashSet<>();
            for (ClassRequests.MatchPair pair : request.pairs()) {
                safety.screenText(pair.chinese(), pair.pinyin(), pair.meaning());
                if (!meanings.add(pair.meaning().toLowerCase(Locale.ROOT))) {
                    throw new ResponseStatusException(BAD_REQUEST, "Each meaning can only be used once.");
                }
            }
            MatchingGame saved = (MatchingGame) revisions.saveActivity(game);
            revisions.deleteMatchingItems(saved.getId());
            int order = 1;
            for (ClassRequests.MatchPair pair : request.pairs()) {
                MatchingGameItem item = new MatchingGameItem();
                item.setMatchingGame(saved);
                item.setLeftText(pair.chinese());
                item.setPinyin(pair.pinyin());
                item.setRightText(pair.meaning());
                item.setSortOrder(order++);
                matchingGameItemRepository.save(item);
            }
        }
    }

    private LessonActivity resolveActivity(Target target, ClassActivityType type, Long activityId) {
        LessonActivity activity = content.findActivity(type, activityId).orElse(null);
        if (activity != null && activity.getLesson() != null
                && activity.getLesson().getId().equals(target.working().getId())
                && ClassContentSupport.isActive(activity)) {
            return activity;
        }
        Long mapped = mappedActivityId(target, type, activityId);
        if (mapped != null) {
            return content.findActivity(type, mapped).orElseThrow();
        }
        throw new ResponseStatusException(NOT_FOUND, ACTIVITY_NOT_FOUND);
    }

    /** The copy in the revision of a published activity the editor addressed before the revision existed. */
    private Long mappedActivityId(Target target, ClassActivityType type, Long sourceId) {
        if (target.working().getRevisionOf() == null) {
            return null;
        }
        return content.activities(target.working().getId()).stream()
                .filter(activity -> ClassContentSupport.typeOf(activity) == type)
                .filter(activity -> sourceId.equals(activity.getRevisionSourceId()))
                .map(LessonActivity::getId)
                .findFirst()
                .orElse(null);
    }

    private int nextPosition(Lesson working) {
        return content.activities(working.getId()).stream()
                .map(LessonActivity::getLessonPosition)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max().orElse(0) + 1;
    }

    private static ClassActivityType parseType(String value) {
        return ClassActivityType.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Activity type must be FLASHCARDS, TRACING or MATCHING."));
    }

    // ----- quizzes -----

    @Transactional
    public TeacherClassViews.LessonEditor addQuiz(SecurityUserDetails currentUser, Long lessonId, QuizRequest request) {
        Target target = edit(currentUser, lessonId);
        Quiz quiz = new Quiz();
        quiz.setLesson(target.working());
        quiz.setTeacher(target.lesson().getTeacher());
        applyQuiz(quiz, request);
        quiz.setTotalPoints(0);
        quiz.setContentStatus(ContentStatus.DRAFT);
        quizRepository.save(quiz);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor updateQuiz(SecurityUserDetails currentUser, Long lessonId, Long quizId, QuizRequest request) {
        Target target = edit(currentUser, lessonId);
        Quiz quiz = resolveQuiz(target, quizId);
        applyQuiz(quiz, request);
        quizRepository.save(quiz);
        touch(target);
        return view(target.lesson());
    }

    /**
     * Archives a quiz at once: students stop seeing it and keep their attempts. In a revision both the published
     * quiz and its copy go, so approving the revision does not bring it back.
     */
    @Transactional
    public TeacherClassViews.LessonEditor archiveQuiz(SecurityUserDetails currentUser, Long lessonId, Long quizId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        Lesson shown = revisions.displayed(lesson);
        if (shown.getContentStatus() == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, LessonRevisionService.IN_REVIEW);
        }
        Quiz quiz = quizRepository.findById(quizId)
                .filter(found -> found.getLesson().getId().equals(lesson.getId()) || found.getLesson().getId().equals(shown.getId()))
                .filter(ClassContentSupport::isActive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND));
        Long sourceId = quiz.getLesson().getId().equals(lesson.getId()) ? quiz.getId() : quiz.getRevisionSourceId();
        if (!quiz.getLesson().getId().equals(lesson.getId())) {
            revisions.deleteQuestions(quiz.getId());
            quizRepository.delete(quiz);
        } else if (shown != lesson) {
            content.quizzes(shown.getId()).stream()
                    .filter(copy -> quizId.equals(copy.getRevisionSourceId()))
                    .forEach(copy -> {
                        revisions.deleteQuestions(copy.getId());
                        quizRepository.delete(copy);
                    });
        }
        if (sourceId != null) {
            quizRepository.findById(sourceId).filter(ClassContentSupport::isActive).ifPresent(source -> {
                source.setStatusBeforeArchive(source.getContentStatus());
                source.setContentStatus(ContentStatus.ARCHIVED);
                quizRepository.save(source);
            });
        }
        lesson.setUpdatedAt(LocalDateTime.now());
        lessonRepository.save(lesson);
        return view(lesson);
    }

    @Transactional
    public TeacherClassViews.LessonEditor duplicateQuiz(SecurityUserDetails currentUser, Long lessonId, Long quizId) {
        Target target = edit(currentUser, lessonId);
        Quiz source = resolveQuiz(target, quizId);
        Quiz copy = revisions.copyQuiz(source, target.working());
        copy.setTitle(copyTitle(source.getTitle(), 200));
        quizRepository.save(copy);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor addQuestion(
            SecurityUserDetails currentUser,
            Long lessonId,
            Long quizId,
            QuizQuestionRequest request) {
        Target target = edit(currentUser, lessonId);
        Quiz quiz = resolveQuiz(target, quizId);
        requireQuestionsEditable(quiz);
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        question.setSortOrder(content.questions(quiz.getId()).stream().mapToInt(QuizQuestion::getSortOrder).max().orElse(0) + 1);
        applyQuestion(question, request);
        recomputeTotal(quiz);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor updateQuestion(
            SecurityUserDetails currentUser,
            Long lessonId,
            Long questionId,
            QuizQuestionRequest request) {
        Target target = edit(currentUser, lessonId);
        QuizQuestion question = resolveQuestion(target, questionId);
        requireQuestionsEditable(question.getQuiz());
        applyQuestion(question, request);
        recomputeTotal(question.getQuiz());
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor deleteQuestion(SecurityUserDetails currentUser, Long lessonId, Long questionId) {
        Target target = edit(currentUser, lessonId);
        QuizQuestion question = resolveQuestion(target, questionId);
        Quiz quiz = question.getQuiz();
        requireQuestionsEditable(quiz);
        revisions.deleteQuestion(question);
        quizQuestionRepository.flush();
        recomputeTotal(quiz);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor duplicateQuestion(SecurityUserDetails currentUser, Long lessonId, Long questionId) {
        Target target = edit(currentUser, lessonId);
        QuizQuestion question = resolveQuestion(target, questionId);
        Quiz quiz = question.getQuiz();
        requireQuestionsEditable(quiz);
        List<QuizQuestion> questions = content.questions(quiz.getId());
        QuizQuestion copy = revisions.copyQuestion(question, quiz,
                quizOptionRepository.findForQuestions(List.of(question)).getOrDefault(question.getId(), List.of()));
        renumber(questions, question, copy);
        recomputeTotal(quiz);
        touch(target);
        return view(target.lesson());
    }

    @Transactional
    public TeacherClassViews.LessonEditor reorderQuestions(
            SecurityUserDetails currentUser,
            Long lessonId,
            Long quizId,
            ClassRequests.QuestionOrder request) {
        Target target = edit(currentUser, lessonId);
        Quiz quiz = resolveQuiz(target, quizId);
        requireQuestionsEditable(quiz);
        List<QuizQuestion> questions = content.questions(quiz.getId());
        Map<Long, QuizQuestion> byId = new HashMap<>();
        questions.forEach(question -> byId.put(question.getId(), question));
        if (request.questionIds().size() != questions.size() || !byId.keySet().containsAll(request.questionIds())) {
            throw new ResponseStatusException(BAD_REQUEST, "The new order must list every question of the quiz once.");
        }
        int order = 1;
        for (Long id : request.questionIds()) {
            QuizQuestion question = byId.get(id);
            question.setSortOrder(order++);
            quizQuestionRepository.save(question);
        }
        touch(target);
        return view(target.lesson());
    }

    /** Puts the copy right after the original and numbers the questions 1..n. */
    private void renumber(List<QuizQuestion> questions, QuizQuestion original, QuizQuestion copy) {
        int order = 1;
        for (QuizQuestion question : questions) {
            question.setSortOrder(order++);
            quizQuestionRepository.save(question);
            if (question.getId().equals(original.getId())) {
                copy.setSortOrder(order++);
                quizQuestionRepository.save(copy);
            }
        }
    }

    private Quiz resolveQuiz(Target target, Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz != null && quiz.getLesson().getId().equals(target.working().getId()) && ClassContentSupport.isActive(quiz)) {
            return quiz;
        }
        if (target.working().getRevisionOf() != null) {
            return content.quizzes(target.working().getId()).stream()
                    .filter(copy -> quizId.equals(copy.getRevisionSourceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND));
        }
        throw new ResponseStatusException(NOT_FOUND, QUIZ_NOT_FOUND);
    }

    /** A question of a quiz in the working copy; a question of the published quiz maps to the same place in its copy. */
    private QuizQuestion resolveQuestion(Target target, Long questionId) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, QUESTION_NOT_FOUND));
        Quiz quiz = question.getQuiz();
        if (quiz.getLesson().getId().equals(target.working().getId())) {
            return question;
        }
        if (target.working().getRevisionOf() != null && quiz.getLesson().getId().equals(target.lesson().getId())) {
            Quiz copy = resolveQuiz(target, quiz.getId());
            int index = content.questions(quiz.getId()).indexOf(question);
            List<QuizQuestion> copied = content.questions(copy.getId());
            if (index >= 0 && index < copied.size()) {
                return copied.get(index);
            }
        }
        throw new ResponseStatusException(NOT_FOUND, QUESTION_NOT_FOUND);
    }

    /** Questions students already answered stay as they are (the quiz of a revision checks its published quiz). */
    private void requireQuestionsEditable(Quiz quiz) {
        Long statsId = quiz.getRevisionSourceId() == null ? quiz.getId() : quiz.getRevisionSourceId();
        if (quizAttemptRepository.existsByQuizIdAndSubmittedTrue(statsId)) {
            throw new ResponseStatusException(BAD_REQUEST, QUESTIONS_LOCKED);
        }
    }

    private void applyQuiz(Quiz quiz, QuizRequest request) {
        quiz.setTitle(safety.requireSafeText(request.title(), 200, "Quiz title is required."));
        quiz.setDescription(safety.optionalSafeText(request.description(), 1000));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes() == null || request.timeLimitMinutes() == 0
                ? null
                : request.timeLimitMinutes());
        quiz.setLearningStage(request.learningStage());
    }

    private void applyQuestion(QuizQuestion question, QuizQuestionRequest request) {
        QuizQuestionType type = QuizQuestionType.fromValue(request.questionType());
        question.setQuestionType(type.name());
        question.setQuestionText(safety.requireSafeText(request.questionText(), 2000, "Question text is required."));
        question.setPoints(request.points());
        question.setExplanation(safety.optionalSafeText(request.explanation(), 2000));
        question.setHint(safety.optionalSafeText(request.hint(), 1000));
        question.setAudioUrl(type.needsAudio()
                ? requireMediaLink(request.audioUrl(), "A listen-and-choose question needs an audio link.", "Audio")
                : null);
        question.setImageUrl(type.needsImage()
                ? requireMediaLink(request.imageUrl(), "A word and image question needs an image link.", "Image")
                : null);
        if (type.isOptionBased()) {
            question.setCorrectAnswerText(correctOptionText(request.options()));
        } else {
            question.setCorrectAnswerText(safety.requireSafeText(
                    request.correctAnswerText(), 1000, "Correct answer text is required for this question type."));
        }
        QuizQuestion saved = quizQuestionRepository.save(question);
        revisions.deleteOptions(saved);
        if (type.isOptionBased()) {
            int order = 1;
            for (QuizOptionRequest option : request.options()) {
                QuizOption entity = new QuizOption();
                entity.setQuestion(saved);
                entity.setOptionText(safety.requireSafeText(option.optionText(), 1000, "Option text is required."));
                entity.setCorrectOption(option.correctOption());
                entity.setSortOrder(order++);
                quizOptionRepository.save(entity);
            }
        }
    }

    private String correctOptionText(List<QuizOptionRequest> options) {
        if (options.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one answer option is required.");
        }
        List<QuizOptionRequest> correct = options.stream().filter(QuizOptionRequest::correctOption).toList();
        if (correct.size() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct answer option is required.");
        }
        return safety.requireSafeText(correct.get(0).optionText(), 1000, "Correct answer option text is required.");
    }

    private static String requireMediaLink(String link, String missingMessage, String mediaLabel) {
        if (link == null || link.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, missingMessage);
        }
        String stripped = link.strip();
        try {
            URI uri = new URI(stripped);
            boolean web = ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && stripped.indexOf('\\') < 0;
            if (web) {
                return stripped;
            }
        } catch (URISyntaxException exception) {
            // reported below
        }
        throw new ResponseStatusException(BAD_REQUEST, mediaLabel
                + " link must be a full http:// or https:// web address without a user name or password.");
    }

    private void recomputeTotal(Quiz quiz) {
        int total = content.questions(quiz.getId()).stream().mapToInt(QuizQuestion::getPoints).sum();
        quiz.setTotalPoints(total);
        quizRepository.save(quiz);
    }

    // ----- review workflow -----

    /** Sends the lesson (or its revision) for review once the checklist is complete. */
    @Transactional
    public TeacherClassViews.LessonEditor submit(SecurityUserDetails currentUser, Long lessonId) {
        Target target = edit(currentUser, lessonId);
        Lesson working = target.working();
        TeacherClassViews.Checklist checklist = checklist(working);
        if (checklist.activityCount() == 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Add at least one practice activity before you submit.");
        }
        if (checklist.quizCount() == 0 || !checklist.quizzesReady()) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Add a quiz with at least 3 questions, each with a correct answer, before you submit.");
        }
        if (!checklist.pinyinComplete()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add pinyin to every new word before you submit.");
        }
        List<TeacherClassViews.Change> changes = working.getRevisionOf() == null ? null : revisions.changes(working);
        if (changes != null && changes.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "There are no saved changes to submit.");
        }
        LocalDateTime now = LocalDateTime.now();
        working.setContentStatus(ContentStatus.SUBMITTED);
        working.setSubmittedAt(now);
        lessonRepository.save(working);
        User teacherUser = target.lesson().getTeacher().getUser();
        if (changes != null) {
            events.record(target.coursePackage(), target.lesson(), teacherUser,
                    ClassContentEventType.REVISION_SUBMITTED, null, changes);
        } else {
            CoursePackage coursePackage = target.coursePackage();
            if (coursePackage.getContentStatus() == ContentStatus.DRAFT || coursePackage.getContentStatus() == ContentStatus.REJECTED) {
                coursePackage.setContentStatus(ContentStatus.SUBMITTED);
                coursePackage.setSubmittedAt(now);
                coursePackage.setRejectionReason(null);
                coursePackageRepository.save(coursePackage);
                events.record(coursePackage, null, teacherUser, ClassContentEventType.CLASS_SUBMITTED,
                        working.getTitle(), null);
            }
            events.record(coursePackage, target.lesson(), teacherUser, ClassContentEventType.LESSON_SUBMITTED);
        }
        return view(target.lesson());
    }

    /** Hides the lesson from students; their progress and attempts are kept. */
    @Transactional
    public TeacherClassViews.LessonEditor archive(SecurityUserDetails currentUser, Long lessonId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        if (lesson.getContentStatus() == ContentStatus.SUBMITTED
                || revisions.displayed(lesson).getContentStatus() == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, LessonRevisionService.IN_REVIEW);
        }
        archiveLesson(lesson, lesson.getTeacher().getUser());
        return view(lesson);
    }

    @Transactional
    public TeacherClassViews.LessonEditor restore(SecurityUserDetails currentUser, Long lessonId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        restoreLesson(lesson, lesson.getTeacher().getUser());
        return view(lesson);
    }

    /** Shared by the teacher and the admin: archive with the status kept for a restore. */
    void archiveLesson(Lesson lesson, User actor) {
        if (lesson.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "This lesson is already archived.");
        }
        lesson.setStatusBeforeArchive(lesson.getContentStatus());
        lesson.setContentStatus(ContentStatus.ARCHIVED);
        lessonRepository.save(lesson);
        events.record(content.requireClassOfLesson(lesson), lesson, actor, ClassContentEventType.LESSON_ARCHIVED);
    }

    void restoreLesson(Lesson lesson, User actor) {
        if (lesson.getContentStatus() != ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only an archived lesson can be restored.");
        }
        lesson.setContentStatus(ContentStatus.afterRestore(lesson.getStatusBeforeArchive()));
        lesson.setStatusBeforeArchive(null);
        lessonRepository.save(lesson);
        events.record(content.requireClassOfLesson(lesson), lesson, actor, ClassContentEventType.LESSON_RESTORED);
    }

    /** Drops the revision of a published lesson; students never saw it. */
    @Transactional
    public TeacherClassViews.LessonEditor discardRevision(SecurityUserDetails currentUser, Long lessonId) {
        access.requireTeacher(currentUser);
        Lesson lesson = access.requireOwnedLesson(currentUser, lessonId);
        Lesson revision = content.revisionOf(lesson)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "This lesson has no changes to discard."));
        if (revision.getContentStatus() == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, LessonRevisionService.IN_REVIEW);
        }
        revisions.deleteLessonTree(revision);
        return view(lesson);
    }

    // ----- view -----

    TeacherClassViews.LessonEditor view(Lesson lesson) {
        CoursePackage coursePackage = content.requireClassOfLesson(lesson);
        Lesson revision = content.revisionOf(lesson).orElse(null);
        Lesson shown = revision != null ? revision : lesson;
        boolean editable = coursePackage.getContentStatus() != ContentStatus.ARCHIVED
                && switch (lesson.getContentStatus()) {
                    case DRAFT, REJECTED -> true;
                    case PUBLISHED -> revision == null || revision.getContentStatus() != ContentStatus.SUBMITTED;
                    default -> false;
                };
        String reviewNote = revision != null ? revision.getRejectionReason()
                : lesson.getContentStatus() == ContentStatus.REJECTED ? lesson.getRejectionReason() : null;
        TeacherClassViews.Event noteEvent = reviewNote == null
                ? null
                : ClassEventService.latestChangesRequested(events.lessonHistory(lesson.getId()));
        List<LearningModule> units = content.units(coursePackage, false);
        RecordedLessonVideo video = content.video(shown.getId()).orElse(null);

        List<TeacherClassViews.Activity> activities = new ArrayList<>();
        int number = 1;
        for (LessonActivity activity : content.activities(shown.getId())) {
            if (ClassContentSupport.isActive(activity)) {
                activities.add(activityView(activity, number++));
            }
        }
        List<TeacherClassViews.Quiz> quizzes = content.quizzes(shown.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .map(this::quizView)
                .toList();
        return new TeacherClassViews.LessonEditor(
                lesson.getId(),
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getContentStatus().name(),
                lesson.getModule().getId(),
                lesson.getModule().getTitle(),
                units.stream().map(unit -> new TeacherClassViews.UnitOption(unit.getId(), unit.getTitle())).toList(),
                lesson.getContentStatus().name(),
                revision == null ? null : TeacherClassService.revisionStatusName(revision.getContentStatus()),
                editable,
                reviewNote,
                noteEvent == null ? null : noteEvent.actorName(),
                noteEvent == null ? null : noteEvent.createdAt(),
                shown.getTitle(),
                shown.getEstimatedDurationMinutes(),
                shown.getSummary(),
                content.keyPhrases(shown.getId()).stream()
                        .map(phrase -> new TeacherClassViews.KeyPhrase(phrase.getChinese(), phrase.getPinyin(), phrase.getMeaning()))
                        .toList(),
                video == null ? null : videoView(video, coursePackage),
                activities,
                quizzes,
                checklist(shown));
    }

    /** The video with how many of the class's students watched it (a revision's copy counts its published video). */
    private TeacherClassViews.Video videoView(RecordedLessonVideo video, CoursePackage coursePackage) {
        Long statsVideoId = video.getRevisionSourceId() == null ? video.getId() : video.getRevisionSourceId();
        int watched = (int) videoProgressRepository.findByRecordedVideoIdIn(List.of(statsVideoId)).stream()
                .filter(VideoProgress::isCompleted)
                .count();
        return new TeacherClassViews.Video(
                video.getTitle(),
                video.getDurationSeconds(),
                watched,
                (int) enrolmentRepository.countByCoursePackageId(coursePackage.getId()));
    }

    private TeacherClassViews.Activity activityView(LessonActivity activity, int number) {
        PracticeActivityContent entity = (PracticeActivityContent) activity;
        List<TeacherClassViews.TraceCharacter> characters = activity instanceof TracingExercise tracing
                ? TracingCharacters.read(tracing).stream()
                        .map(entry -> new TeacherClassViews.TraceCharacter(entry.character(), entry.pinyin(), entry.meaning()))
                        .toList()
                : List.of();
        List<TeacherClassViews.MatchPair> pairs = activity instanceof MatchingGame game
                ? content.matchingItems(game.getId()).stream()
                        .map(item -> new TeacherClassViews.MatchPair(item.getLeftText(), item.getPinyin(), item.getRightText()))
                        .toList()
                : List.of();
        return new TeacherClassViews.Activity(
                ClassContentSupport.typeOf(activity).name(),
                activity.getId(),
                activity.getTitle(),
                entity.getLearningStage() == null ? null : entity.getLearningStage().name(),
                number,
                characters,
                pairs);
    }

    private TeacherClassViews.Quiz quizView(Quiz quiz) {
        List<QuizQuestion> questions = content.questions(quiz.getId());
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(questions);
        Long statsId = quiz.getRevisionSourceId() == null ? quiz.getId() : quiz.getRevisionSourceId();
        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizIdInAndSubmittedTrueOrderByCompletedAtDescIdDesc(List.of(statsId));
        List<TeacherClassViews.Question> questionViews = new ArrayList<>();
        int number = 1;
        for (QuizQuestion question : questions) {
            questionViews.add(new TeacherClassViews.Question(
                    question.getId(),
                    number++,
                    question.getQuestionType(),
                    question.getQuestionText(),
                    question.getPoints(),
                    question.getCorrectAnswerText(),
                    question.getExplanation(),
                    question.getHint(),
                    question.getAudioUrl(),
                    question.getImageUrl(),
                    options.getOrDefault(question.getId(), List.of()).stream()
                            .map(option -> new TeacherClassViews.QuestionOption(option.getId(), option.getOptionText(), option.isCorrectOption()))
                            .toList()));
        }
        return new TeacherClassViews.Quiz(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getLearningStage() == null ? null : quiz.getLearningStage().name(),
                quiz.getTimeLimitMinutes(),
                questions.stream().mapToInt(QuizQuestion::getPoints).sum(),
                !attempts.isEmpty(),
                attempts.size(),
                ClassProgressService.average(attempts.stream().map(ClassProgressService::attemptPercent).toList()),
                attempts.stream().limit(5)
                        .map(attempt -> new TeacherClassViews.AttemptSummary(
                                attempt.getStudent().getUser().getDisplayName(),
                                attempt.getCompletedAt(),
                                ClassProgressService.attemptPercent(attempt)))
                        .toList(),
                questionViews);
    }

    /** The submit checklist of the working copy. */
    TeacherClassViews.Checklist checklist(Lesson working) {
        List<LessonActivity> activities = content.activities(working.getId()).stream().filter(ClassContentSupport::isActive).toList();
        List<Quiz> quizzes = content.quizzes(working.getId()).stream().filter(ClassContentSupport::isActive).toList();
        Map<Long, List<QuizQuestion>> questions = content.questionsByQuiz(quizzes.stream().map(Quiz::getId).toList());
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(
                questions.values().stream().flatMap(List::stream).toList());
        boolean quizzesReady = !quizzes.isEmpty() && quizzes.stream().allMatch(quiz -> {
            List<QuizQuestion> quizQuestions = questions.getOrDefault(quiz.getId(), List.of());
            return quizQuestions.size() >= MIN_QUIZ_QUESTIONS
                    && quizQuestions.stream().allMatch(question -> hasCorrectAnswer(question, options.getOrDefault(question.getId(), List.of())));
        });
        boolean pinyinComplete = content.keyPhrases(working.getId()).stream().allMatch(phrase -> notBlank(phrase.getPinyin()))
                && activities.stream().allMatch(this::activityHasPinyin);
        return new TeacherClassViews.Checklist(
                activities.size(),
                quizzes.size(),
                questions.values().stream().mapToInt(List::size).sum(),
                quizzesReady,
                pinyinComplete);
    }

    private boolean activityHasPinyin(LessonActivity activity) {
        if (activity instanceof TracingExercise tracing) {
            return TracingCharacters.read(tracing).stream().allMatch(entry -> notBlank(entry.pinyin()));
        }
        if (activity instanceof MatchingGame game) {
            return content.matchingItems(game.getId()).stream().allMatch(item -> notBlank(item.getPinyin()));
        }
        return true;
    }

    private static boolean hasCorrectAnswer(QuizQuestion question, List<QuizOption> options) {
        boolean optionBased = QuizQuestionType.tryFrom(question.getQuestionType()).map(QuizQuestionType::isOptionBased).orElse(false);
        return optionBased
                ? options.stream().filter(QuizOption::isCorrectOption).count() == 1
                : notBlank(question.getCorrectAnswerText());
    }

    private void touch(Target target) {
        target.working().setUpdatedAt(LocalDateTime.now());
        lessonRepository.save(target.working());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String copyTitle(String title, int max) {
        String copy = title + " (copy)";
        return copy.length() <= max ? copy : copy.substring(0, max);
    }
}
