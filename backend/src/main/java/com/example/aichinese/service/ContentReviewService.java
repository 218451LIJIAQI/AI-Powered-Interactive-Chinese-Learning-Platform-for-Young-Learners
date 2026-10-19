package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.review.ContentReviewDetailResponse;
import com.example.aichinese.dto.review.ContentReviewItemResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.base.ReviewableContent;
import com.example.aichinese.entity.StoryActivity;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.entity.enums.ReviewContentType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.mapper.AdminActivityMapper;
import com.example.aichinese.mapper.ContentManagementMapper;
import com.example.aichinese.mapper.QuizMapper;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.DragDropActivityRepository;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.StoryQuestionRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Predicate;

/**
 * The content review workflow (decisions D4, D5, D14 and D24) for courses, modules, lessons,
 * quizzes and the six practice-activity types.
 *
 * <ul>
 *   <li>Teachers: create -> DRAFT; {@link #submitForReview} DRAFT/REJECTED -> SUBMITTED;
 *       editing PUBLISHED content moves it back to SUBMITTED ({@link #recordTeacherEdit}).</li>
 *   <li>Admins: approve SUBMITTED -> PUBLISHED; reject SUBMITTED/PUBLISHED -> REJECTED with a
 *       reason; archive -> ARCHIVED; restore ARCHIVED -> PUBLISHED.</li>
 * </ul>
 *
 * <p>Every decision notifies the other party and writes an audit-log entry. Completeness rules
 * ({@link #requireComplete}) are checked on submit, approve and restore so empty quizzes or
 * activities never reach students.</p>
 */
@Service
public class ContentReviewService {

    public static final String ADMIN_REVIEW_PATH = "/admin/content-review";
    public static final String TEACHER_CONTENT_PATH = "/teacher/content";
    private static final String STATUS_ALL = "ALL";

    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MERGE_BATCH_SIZE = 100;
    private static final int MIN_MATCHING_PAIRS = 2;
    private static final int MIN_STORY_OPTIONS = 2;

    private final CoursePackageRepository coursePackageRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final PackageItemRepository packageItemRepository;
    private final FlashcardRepository flashcardRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final DragDropActivityRepository dragDropActivityRepository;
    private final DragDropItemRepository dragDropItemRepository;
    private final StoryActivityRepository storyActivityRepository;
    private final StoryQuestionRepository storyQuestionRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final UserRepository userRepository;
    private final ContentManagementMapper contentManagementMapper;
    private final QuizMapper quizMapper;
    private final AdminActivityMapper adminActivityMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ContentReviewService(
            CoursePackageRepository coursePackageRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            PackageItemRepository packageItemRepository,
            FlashcardRepository flashcardRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            DragDropActivityRepository dragDropActivityRepository,
            DragDropItemRepository dragDropItemRepository,
            StoryActivityRepository storyActivityRepository,
            StoryQuestionRepository storyQuestionRepository,
            TeacherProfileRepository teacherProfileRepository,
            UserRepository userRepository,
            ContentManagementMapper contentManagementMapper,
            QuizMapper quizMapper,
            AdminActivityMapper adminActivityMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.quizRepository = Objects.requireNonNull(quizRepository, "quizRepository");
        this.quizQuestionRepository = Objects.requireNonNull(quizQuestionRepository, "quizQuestionRepository");
        this.quizOptionRepository = Objects.requireNonNull(quizOptionRepository, "quizOptionRepository");
        this.packageItemRepository = Objects.requireNonNull(packageItemRepository, "packageItemRepository");
        this.flashcardRepository = Objects.requireNonNull(flashcardRepository, "flashcardRepository");
        this.matchingGameRepository = Objects.requireNonNull(matchingGameRepository, "matchingGameRepository");
        this.matchingGameItemRepository = Objects.requireNonNull(matchingGameItemRepository, "matchingGameItemRepository");
        this.listeningExerciseRepository = Objects.requireNonNull(listeningExerciseRepository, "listeningExerciseRepository");
        this.tracingExerciseRepository = Objects.requireNonNull(tracingExerciseRepository, "tracingExerciseRepository");
        this.dragDropActivityRepository = Objects.requireNonNull(dragDropActivityRepository, "dragDropActivityRepository");
        this.dragDropItemRepository = Objects.requireNonNull(dragDropItemRepository, "dragDropItemRepository");
        this.storyActivityRepository = Objects.requireNonNull(storyActivityRepository, "storyActivityRepository");
        this.storyQuestionRepository = Objects.requireNonNull(storyQuestionRepository, "storyQuestionRepository");
        this.teacherProfileRepository = Objects.requireNonNull(teacherProfileRepository, "teacherProfileRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.contentManagementMapper = Objects.requireNonNull(contentManagementMapper, "contentManagementMapper");
        this.quizMapper = Objects.requireNonNull(quizMapper, "quizMapper");
        this.adminActivityMapper = Objects.requireNonNull(adminActivityMapper, "adminActivityMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    // ------------------------------------------------------------------
    // Teacher-side workflow hooks
    // ------------------------------------------------------------------

    /** Rejects edits of content that is under review or archived. */
    public void requireTeacherEditable(ReviewableContent item, ReviewContentType type) {
        ContentStatus status = item.getContentStatus();
        if (status == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This " + lower(type) + " is waiting for admin review and cannot be edited until it has been reviewed.");
        }
        if (status == ContentStatus.ARCHIVED || status == ContentStatus.APPROVED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    type.label() + " cannot be edited once it has been archived.");
        }
    }

    /**
     * Applies D24 after a teacher edit: editing PUBLISHED content moves it back to SUBMITTED
     * (hidden from students until approved again; learner progress is kept) and tells admins.
     *
     * @return {@code true} when the edit sent the item back for review
     */
    public boolean recordTeacherEdit(User teacherUser, ReviewContentType type, ReviewableContent item) {
        if (item.getContentStatus() != ContentStatus.PUBLISHED) {
            return false;
        }
        item.setContentStatus(ContentStatus.SUBMITTED);
        item.setRejectionReason(null);
        notifyAdmins(
                "Published " + lower(type) + " edited",
                safeName(teacherUser) + " edited the published " + lower(type) + " \"" + displayTitle(item)
                        + "\". It is hidden from students until it is approved again.",
                type,
                item,
                teacherUser);
        auditLogService.record(teacherUser, "CONTENT_RESUBMIT_AFTER_EDIT", type.name(), item.getId(),
                "Teacher edited published " + lower(type) + "; it was sent back for review.");
        return true;
    }

    /** DRAFT or REJECTED -> SUBMITTED after the completeness check; admins are notified. */
    public void submitForReview(User teacherUser, ReviewContentType type, ReviewableContent item) {
        ContentStatus status = item.getContentStatus();
        if (status == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This " + lower(type) + " is already waiting for admin review.");
        }
        if (status == ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "This " + lower(type) + " is published. Edit it to send changes for review.");
        }
        if (!ContentStatus.TEACHER_SUBMITTABLE.contains(status)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Archived " + lower(type) + " content cannot be submitted for review.");
        }
        requireComplete(type, item);
        item.setContentStatus(ContentStatus.SUBMITTED);
        item.setRejectionReason(null);
        notifyAdmins(
                type.label() + " submitted for review",
                safeName(teacherUser) + " submitted the " + lower(type) + " \"" + displayTitle(item) + "\" for review.",
                type,
                item,
                teacherUser);
        auditLogService.record(teacherUser, "CONTENT_SUBMIT", type.name(), item.getId(),
                "Teacher submitted " + lower(type) + " for admin review.");
    }

    /** Completeness rules applied on submit, approve, restore and admin publish. */
    @Transactional(readOnly = true)
    public void requireComplete(ReviewContentType type, ReviewableContent item) {
        switch (type) {
            case PACKAGE -> {
                if (packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(item.getId()).isEmpty()) {
                    incomplete("Add at least one module or lesson to this course before it is reviewed.");
                }
            }
            case MODULE -> {
                if (TextUtils.trimToNull(item.getTitle()) == null) {
                    incomplete("A module title is required before it is reviewed.");
                }
            }
            case LESSON -> requireCompleteLesson((Lesson) item);
            case QUIZ -> requireCompleteQuiz((Quiz) item);
            case FLASHCARD -> {
                Flashcard flashcard = (Flashcard) item;
                if (isBlank(flashcard.getFrontText()) || isBlank(flashcard.getBackText())) {
                    incomplete("A flashcard needs front and back text before it is reviewed.");
                }
            }
            case MATCHING_GAME -> {
                if (matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(item.getId()).size()
                        < MIN_MATCHING_PAIRS) {
                    incomplete("A matching game needs at least " + MIN_MATCHING_PAIRS + " pairs before it is reviewed.");
                }
            }
            case LISTENING_EXERCISE -> {
                ListeningExercise listening = (ListeningExercise) item;
                if (isBlank(listening.getAudioUrl()) || isBlank(listening.getQuestionPayload())) {
                    incomplete("A listening exercise needs an audio URL and questions before it is reviewed.");
                }
                // Completion is server-validated, so every question needs an answer key (one of its options).
                if (!ActivityAnswerKeys.parseListening(listening.getQuestionPayload()).answerable()) {
                    incomplete("Each listening question needs an answer (one of its options) before it is reviewed. "
                            + "Use {\"prompt\":\"...\",\"questions\":[{\"question\":\"...\",\"options\":[\"...\"],\"answer\":\"...\"}]}.");
                }
            }
            case TRACING_EXERCISE -> {
                TracingExercise tracing = (TracingExercise) item;
                if (isBlank(tracing.getTargetCharacter()) || isBlank(tracing.getWorksheetUrl())) {
                    incomplete("A tracing exercise needs a target character and a worksheet URL before it is reviewed.");
                }
            }
            case DRAG_DROP_ACTIVITY -> {
                if (dragDropItemRepository.findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(item.getId()).isEmpty()) {
                    incomplete("A drag-and-drop activity needs at least one item before it is reviewed.");
                }
            }
            case STORY_ACTIVITY -> {
                StoryActivity story = (StoryActivity) item;
                List<StoryQuestion> storyQuestions =
                        storyQuestionRepository.findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(item.getId());
                if (isBlank(story.getStoryText()) || storyQuestions.isEmpty()) {
                    incomplete("A story activity needs story text and at least one question before it is reviewed.");
                }
                for (StoryQuestion question : storyQuestions) {
                    List<ActivityAnswerKeys.StoryOption> options = ActivityAnswerKeys.parseStoryOptions(question.getOptionsPayload());
                    if (options.size() < MIN_STORY_OPTIONS || ActivityAnswerKeys.storyCorrectIndex(question, options) == null) {
                        incomplete("Each story question needs at least " + MIN_STORY_OPTIONS
                                + " options and a correct option before it is reviewed.");
                    }
                }
            }
        }
    }

    private void requireCompleteLesson(Lesson lesson) {
        if (isBlank(lesson.getLessonContent()) && isBlank(lesson.getSummary())) {
            incomplete("Add lesson content or a summary before this lesson is reviewed.");
        }
        LearningModule module = lesson.getModule();
        if (module != null) {
            if (module.getContentStatus() == ContentStatus.ARCHIVED) {
                incomplete("This lesson belongs to an archived module. Choose another module first.");
            }
            Long moduleTeacherId = module.getTeacher() == null ? null : module.getTeacher().getId();
            Long lessonTeacherId = lesson.getTeacher() == null ? null : lesson.getTeacher().getId();
            if (!Objects.equals(moduleTeacherId, lessonTeacherId)) {
                incomplete("A lesson must belong to a module owned by the same teacher.");
            }
        }
    }

    private void requireCompleteQuiz(Quiz quiz) {
        if (quiz.getLesson() == null || quiz.getLesson().getContentStatus() == ContentStatus.ARCHIVED) {
            incomplete("This quiz belongs to an archived lesson and cannot be reviewed.");
        }
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        if (questions.isEmpty()) {
            incomplete("Add at least one question before this quiz is reviewed.");
        }
        Map<Long, List<QuizOption>> optionsByQuestionId = loadOptionsByQuestionId(questions);
        int totalPoints = 0;
        for (QuizQuestion question : questions) {
            totalPoints += question.getPoints() == null ? 0 : question.getPoints();
            QuizQuestionType questionType = QuizQuestionType.tryFrom(question.getQuestionType()).orElse(null);
            if (questionType == null || isBlank(question.getQuestionText())) {
                incomplete("Every quiz question needs a valid type and question text.");
            }
            if (questionType.isOptionBased()) {
                List<QuizOption> options = optionsByQuestionId.getOrDefault(question.getId(), List.of());
                long correct = options.stream().filter(QuizOption::isCorrectOption).count();
                if (options.isEmpty() || correct != 1) {
                    incomplete("Every multiple-choice quiz question needs exactly one correct option.");
                }
            } else if (isBlank(question.getCorrectAnswerText())) {
                incomplete("Every quiz question needs a correct answer.");
            }
        }
        if (totalPoints <= 0) {
            incomplete("Quiz questions must be worth more than 0 points in total.");
        }
    }

    /** Options of all the given questions in one query, grouped by question id in display order. */
    private Map<Long, List<QuizOption>> loadOptionsByQuestionId(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<Long> questionIds = questions.stream().map(QuizQuestion::getId).toList();
        return quizMapper.groupOptionsByQuestionId(
                quizOptionRepository.findByQuestionIdInOrderBySortOrderAscCreatedAtAsc(questionIds));
    }

    // ------------------------------------------------------------------
    // Admin review queue
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ContentReviewItemResponse> listQueue(
            String status,
            String type,
            String keyword,
            int page,
            int size) {
        ContentStatus statusFilter = parseQueueStatus(status);
        ReviewContentType typeFilter = TextUtils.trimToNull(type) == null ? null : parseType(type);
        String normalizedKeyword = TextUtils.trimToOptional(keyword)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        Map<Long, Optional<TeacherProfile>> teacherCache = new HashMap<>();

        List<QueueCursor<?>> cursors = new ArrayList<>();
        addCursor(cursors, typeFilter, ReviewContentType.PACKAGE, coursePackageRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.MODULE, learningModuleRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.LESSON, lessonRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.QUIZ, quizRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.FLASHCARD, flashcardRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.MATCHING_GAME, matchingGameRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.LISTENING_EXERCISE, listeningExerciseRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.TRACING_EXERCISE, tracingExerciseRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.DRAG_DROP_ACTIVITY, dragDropActivityRepository, statusFilter, normalizedKeyword, sort, teacherCache);
        addCursor(cursors, typeFilter, ReviewContentType.STORY_ACTIVITY, storyActivityRepository, statusFilter, normalizedKeyword, sort, teacherCache);

        long totalItems = cursors.stream().mapToLong(QueueCursor::totalItems).sum();
        List<ContentReviewItemResponse> items = new ArrayList<>(safeSize);
        long firstIndex = (long) safePage * safeSize;
        if (firstIndex < totalItems) {
            Comparator<ContentReviewItemResponse> newestFirst = Comparator
                    .comparing(ContentReviewItemResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(item -> item.type().ordinal())
                    .thenComparing(ContentReviewItemResponse::id, Comparator.reverseOrder());
            long seen = 0;
            while (seen < firstIndex + safeSize) {
                QueueCursor<?> next = null;
                ContentReviewItemResponse nextItem = null;
                for (QueueCursor<?> cursor : cursors) {
                    ContentReviewItemResponse candidate = cursor.peek();
                    if (candidate != null && (nextItem == null || newestFirst.compare(candidate, nextItem) < 0)) {
                        next = cursor;
                        nextItem = candidate;
                    }
                }
                if (next == null) {
                    break;
                }
                next.advance();
                if (seen++ >= firstIndex) {
                    items.add(nextItem);
                }
            }
        }
        return new PageResponse<>(items, safePage, safeSize, totalItems, PaginationUtils.totalPages(totalItems, safeSize));
    }

    @Transactional(readOnly = true)
    public ContentReviewDetailResponse getDetail(ReviewContentType type, Long id) {
        ReviewableContent item = load(type, id);
        return new ContentReviewDetailResponse(toItem(type, item, new HashMap<>()), detailOf(type, item));
    }

    @Transactional
    public ContentReviewItemResponse approve(SecurityUserDetails currentUser, ReviewContentType type, Long id, String note) {
        User admin = requireUser(currentUser);
        ReviewableContent item = load(type, id);
        if (item.getContentStatus() != ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only submitted content can be approved.");
        }
        requireComplete(type, item);
        item.setContentStatus(ContentStatus.PUBLISHED);
        item.setRejectionReason(null);
        String cleanNote = sanitizeOptional(note);
        notifyOwner(type, item, admin,
                type.label() + " approved",
                "Your " + lower(type) + " \"" + displayTitle(item) + "\" was approved and is now published."
                        + (cleanNote == null ? "" : " Note: " + cleanNote));
        auditLogService.record(admin, "CONTENT_APPROVE", type.name(), item.getId(),
                "Admin approved " + lower(type) + " for publication." + noteSuffix(cleanNote));
        return toItem(type, item, new HashMap<>());
    }

    @Transactional
    public ContentReviewItemResponse reject(SecurityUserDetails currentUser, ReviewContentType type, Long id, String reason) {
        User admin = requireUser(currentUser);
        ReviewableContent item = load(type, id);
        String cleanReason = requireReason(reason);
        if (item.getContentStatus() != ContentStatus.SUBMITTED && item.getContentStatus() != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only submitted or published content can be rejected.");
        }
        applyRejection(admin, type, item, cleanReason, "CONTENT_REJECT");
        return toItem(type, item, new HashMap<>());
    }

    @Transactional
    public ContentReviewItemResponse archive(SecurityUserDetails currentUser, ReviewContentType type, Long id, String note) {
        User admin = requireUser(currentUser);
        ReviewableContent item = load(type, id);
        if (item.getContentStatus() == ContentStatus.ARCHIVED) {
            throw new ResponseStatusException(BAD_REQUEST, "This " + lower(type) + " is archived already.");
        }
        applyArchive(admin, type, item, sanitizeOptional(note), "CONTENT_ARCHIVE");
        return toItem(type, item, new HashMap<>());
    }

    @Transactional
    public ContentReviewItemResponse restore(SecurityUserDetails currentUser, ReviewContentType type, Long id, String note) {
        User admin = requireUser(currentUser);
        ReviewableContent item = load(type, id);
        if (item.getContentStatus() != ContentStatus.ARCHIVED && item.getContentStatus() != ContentStatus.APPROVED) {
            throw new ResponseStatusException(BAD_REQUEST, "Only archived content can be restored.");
        }
        applyRestore(admin, type, item, sanitizeOptional(note), "CONTENT_RESTORE");
        return toItem(type, item, new HashMap<>());
    }

    /** REJECTED with the reason stored on the item; the teacher sees the reason. */
    private void applyRejection(User admin, ReviewContentType type, ReviewableContent item, String reason, String auditAction) {
        item.setContentStatus(ContentStatus.REJECTED);
        item.setRejectionReason(reason);
        notifyOwner(type, item, admin,
                type.label() + " returned for changes",
                "Your " + lower(type) + " \"" + displayTitle(item) + "\" needs changes before it can be published. Reason: "
                        + reason);
        auditLogService.record(admin, auditAction, type.name(), item.getId(),
                "Admin rejected " + lower(type) + ". Reason: " + reason);
    }

    /**
     * ARCHIVED (history and learner progress are kept); the teacher is notified (also used by the
     * admin module/lesson/course archive in {@link AdminContentReviewService}).
     */
    public void applyArchive(User admin, ReviewContentType type, ReviewableContent item, String note, String auditAction) {
        item.setContentStatus(ContentStatus.ARCHIVED);
        notifyOwner(type, item, admin,
                type.label() + " archived",
                "Your " + lower(type) + " \"" + displayTitle(item) + "\" was archived by an admin and is hidden from students."
                        + (note == null ? "" : " Note: " + note));
        auditLogService.record(admin, auditAction, type.name(), item.getId(),
                "Admin archived " + lower(type) + "." + noteSuffix(note));
    }

    /** ARCHIVED -> PUBLISHED after the completeness check; the teacher is notified. */
    private void applyRestore(User admin, ReviewContentType type, ReviewableContent item, String note, String auditAction) {
        requireComplete(type, item);
        item.setContentStatus(ContentStatus.PUBLISHED);
        item.setRejectionReason(null);
        notifyOwner(type, item, admin,
                type.label() + " restored",
                "Your " + lower(type) + " \"" + displayTitle(item) + "\" was restored and is published again."
                        + (note == null ? "" : " Note: " + note));
        auditLogService.record(admin, auditAction, type.name(), item.getId(),
                "Admin restored " + lower(type) + " to published." + noteSuffix(note));
    }

    public static ReviewContentType parseType(String raw) {
        return ReviewContentType.tryFrom(raw)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid content type filter."));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private ReviewableContent load(ReviewContentType type, Long id) {
        Optional<? extends ReviewableContent> found = switch (type) {
            case PACKAGE -> coursePackageRepository.findById(id);
            case MODULE -> learningModuleRepository.findById(id);
            case LESSON -> lessonRepository.findById(id);
            case QUIZ -> quizRepository.findById(id);
            case FLASHCARD -> flashcardRepository.findById(id);
            case MATCHING_GAME -> matchingGameRepository.findById(id);
            case LISTENING_EXERCISE -> listeningExerciseRepository.findById(id);
            case TRACING_EXERCISE -> tracingExerciseRepository.findById(id);
            case DRAG_DROP_ACTIVITY -> dragDropActivityRepository.findById(id);
            case STORY_ACTIVITY -> storyActivityRepository.findById(id);
        };
        return found.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, type.label() + " was not found."));
    }

    private Object detailOf(ReviewContentType type, ReviewableContent item) {
        return switch (type) {
            case PACKAGE -> contentManagementMapper.toSubmissionDetailResponse(
                    (CoursePackage) item,
                    packageItemRepository.findByCoursePackageIdOrderBySortOrderAscCreatedAtAsc(
                            item.getId(), PageRequest.of(0, MERGE_BATCH_SIZE)).getContent());
            case MODULE -> contentManagementMapper.toSubmissionDetailResponse((LearningModule) item, List.of());
            case LESSON -> contentManagementMapper.toSubmissionDetailResponse((Lesson) item);
            case QUIZ -> {
                List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(item.getId());
                yield quizMapper.toTeacherQuizResponse((Quiz) item, questions, loadOptionsByQuestionId(questions));
            }
            case FLASHCARD -> adminActivityMapper.toResponse((Flashcard) item);
            case MATCHING_GAME -> adminActivityMapper.toResponse(
                    (MatchingGame) item,
                    matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(item.getId()));
            case LISTENING_EXERCISE -> adminActivityMapper.toResponse((ListeningExercise) item);
            case TRACING_EXERCISE -> adminActivityMapper.toResponse((TracingExercise) item);
            case DRAG_DROP_ACTIVITY -> adminActivityMapper.toResponse(
                    (DragDropActivity) item,
                    dragDropItemRepository.findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(item.getId()));
            case STORY_ACTIVITY -> adminActivityMapper.toResponse(
                    (StoryActivity) item,
                    storyQuestionRepository.findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(item.getId()));
        };
    }

    private ContentReviewItemResponse toItem(
            ReviewContentType type,
            ReviewableContent item,
            Map<Long, Optional<TeacherProfile>> teacherCache) {
        User owner = ownerOf(type, item);
        TeacherStatus ownerTeacherStatus = owner == null
                ? null
                : teacherCache.computeIfAbsent(owner.getId(), teacherProfileRepository::findByUserId)
                        .map(TeacherProfile::getTeacherStatus)
                        .orElse(null);
        return new ContentReviewItemResponse(
                type,
                item.getId(),
                displayTitle(item),
                item.getContentStatus(),
                item.getRejectionReason(),
                learningStageOf(item),
                owner == null ? null : owner.getId(),
                owner == null ? null : owner.getDisplayName(),
                ownerTeacherStatus,
                parentTitleOf(type, item),
                createdAtOf(item),
                updatedAtOf(item));
    }

    private static User ownerOf(ReviewContentType type, ReviewableContent item) {
        return switch (type) {
            case PACKAGE -> teacherUser(((CoursePackage) item).getTeacher());
            case MODULE -> teacherUser(((LearningModule) item).getTeacher());
            case LESSON -> teacherUser(((Lesson) item).getTeacher());
            case QUIZ -> {
                Quiz quiz = (Quiz) item;
                TeacherProfile teacher = quiz.getTeacher() != null
                        ? quiz.getTeacher()
                        : quiz.getLesson() == null ? null : quiz.getLesson().getTeacher();
                yield teacherUser(teacher);
            }
            default -> ((PracticeActivityContent) item).getCreatedByUser();
        };
    }

    private static String parentTitleOf(ReviewContentType type, ReviewableContent item) {
        return switch (type) {
            case LESSON -> {
                LearningModule module = ((Lesson) item).getModule();
                yield module == null ? null : module.getTitle();
            }
            case QUIZ -> {
                Lesson lesson = ((Quiz) item).getLesson();
                yield lesson == null ? null : lesson.getTitle();
            }
            default -> null;
        };
    }

    private static LearningStage learningStageOf(ReviewableContent item) {
        if (item instanceof Quiz quiz) {
            return quiz.getLearningStage();
        }
        if (item instanceof PracticeActivityContent activity) {
            return activity.getLearningStage();
        }
        return null;
    }

    private static LocalDateTime createdAtOf(ReviewableContent item) {
        return item instanceof BaseEntity entity ? entity.getCreatedAt() : null;
    }

    private static LocalDateTime updatedAtOf(ReviewableContent item) {
        return item instanceof BaseEntity entity ? entity.getUpdatedAt() : null;
    }

    private static User teacherUser(TeacherProfile teacher) {
        return teacher == null ? null : teacher.getUser();
    }

    private void notifyOwner(ReviewContentType type, ReviewableContent item, User actor, String title, String message) {
        User owner = ownerOf(type, item);
        if (owner == null || (actor != null && Objects.equals(owner.getId(), actor.getId()))) {
            return;
        }
        notificationService.safeCreateNotification(
                owner,
                NotificationType.CONTENT_REVIEW,
                title,
                message,
                type.name(),
                item.getId(),
                TEACHER_CONTENT_PATH,
                actor);
    }

    private void notifyAdmins(String title, String message, ReviewContentType type, ReviewableContent item, User actor) {
        for (User admin : userRepository.findAllByRoleName(RoleName.ADMIN)) {
            if (actor != null && Objects.equals(admin.getId(), actor.getId())) {
                continue;
            }
            notificationService.safeCreateNotification(
                    admin,
                    NotificationType.CONTENT_REVIEW,
                    title,
                    message,
                    type.name(),
                    item.getId(),
                    ADMIN_REVIEW_PATH,
                    actor);
        }
    }

    private <T extends ReviewableContent> void addCursor(
            List<QueueCursor<?>> cursors,
            ReviewContentType typeFilter,
            ReviewContentType type,
            JpaSpecificationExecutor<T> repository,
            ContentStatus status,
            String keyword,
            Sort sort,
            Map<Long, Optional<TeacherProfile>> teacherCache) {
        if (typeFilter != null && typeFilter != type) {
            return;
        }
        Specification<T> specification = queueSpecification(status, keyword);
        cursors.add(new QueueCursor<>(
                pageable -> repository.findAll(specification, pageable),
                entity -> toItem(type, entity, teacherCache),
                sort));
    }

    private static <T> Specification<T> queueSpecification(ContentStatus status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("contentStatus"), status));
            }
            if (keyword != null) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.<String>get("title"), "")), "%" + keyword + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static ContentStatus parseQueueStatus(String status) {
        String normalized = TextUtils.trimToNull(status);
        if (normalized == null) {
            return ContentStatus.SUBMITTED;
        }
        if (STATUS_ALL.equalsIgnoreCase(normalized)) {
            return null;
        }
        return ContentStatus.tryFrom(normalized)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid content status filter."));
    }

    private User requireUser(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
    }

    private static String requireReason(String reason) {
        String sanitized = AiInputSanitizer.sanitize(reason, MAX_REASON_LENGTH);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Rejection reason is required.");
        }
        return sanitized;
    }

    private static String sanitizeOptional(String note) {
        String sanitized = AiInputSanitizer.sanitize(note, MAX_REASON_LENGTH);
        return sanitized == null || sanitized.isBlank() ? null : sanitized;
    }

    private static String noteSuffix(String note) {
        return note == null ? "" : " Note: " + note;
    }

    private static String displayTitle(ReviewableContent item) {
        String title = TextUtils.trimToNull(item.getTitle());
        if (title == null && item instanceof Flashcard flashcard) {
            title = TextUtils.trimToNull(flashcard.getFrontText());
        }
        return title == null ? "(untitled)" : title;
    }

    private static String safeName(User user) {
        return user == null || TextUtils.trimToNull(user.getDisplayName()) == null ? "A teacher" : user.getDisplayName();
    }

    private static String lower(ReviewContentType type) {
        return type.label().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void incomplete(String message) {
        throw new ResponseStatusException(BAD_REQUEST, message);
    }

    private static final class QueueCursor<T> {
        private final Function<Pageable, Page<T>> fetch;
        private final Function<T, ContentReviewItemResponse> map;
        private final Sort sort;
        private List<T> batch;
        private int page;
        private int index;
        private long totalItems;
        private ContentReviewItemResponse pending;

        private QueueCursor(Function<Pageable, Page<T>> fetch, Function<T, ContentReviewItemResponse> map, Sort sort) {
            this.fetch = fetch;
            this.map = map;
            this.sort = sort;
            loadPage();
        }

        private long totalItems() {
            return totalItems;
        }

        private ContentReviewItemResponse peek() {
            if (index >= batch.size()) {
                if ((long) page * MERGE_BATCH_SIZE >= totalItems) {
                    return null;
                }
                loadPage();
            }
            if (index >= batch.size()) {
                return null;
            }
            if (pending == null) {
                pending = map.apply(batch.get(index));
            }
            return pending;
        }

        private void advance() {
            index++;
            pending = null;
        }

        private void loadPage() {
            Page<T> result = fetch.apply(PageRequest.of(page++, MERGE_BATCH_SIZE, sort));
            totalItems = result.getTotalElements();
            batch = result.getContent();
            index = 0;
            pending = null;
        }
    }
}
