package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.CreateFlashcardRequest;
import com.example.aichinese.dto.learning.CreateListeningExerciseRequest;
import com.example.aichinese.dto.learning.CreateMatchingGameRequest;
import com.example.aichinese.dto.learning.CreateQuizQuestionRequest;
import com.example.aichinese.dto.learning.CreateQuizRequest;
import com.example.aichinese.dto.learning.CreateTracingExerciseRequest;
import com.example.aichinese.dto.learning.FlashcardOptionResponse;
import com.example.aichinese.dto.learning.ListeningExerciseResponse;
import com.example.aichinese.dto.learning.MatchingGameResponse;
import com.example.aichinese.dto.learning.TeacherQuizAttemptSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.dto.learning.TracingExerciseResponse;
import com.example.aichinese.dto.learning.UpdateFlashcardRequest;
import com.example.aichinese.dto.learning.UpdateListeningExerciseRequest;
import com.example.aichinese.dto.learning.UpdateMatchingGameRequest;
import com.example.aichinese.dto.learning.UpdateQuizQuestionRequest;
import com.example.aichinese.dto.learning.UpdateQuizRequest;
import com.example.aichinese.dto.learning.UpdateTracingExerciseRequest;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.mapper.LearningActivityMapper;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class TeacherLearningActivityService {

    private final TeacherContentAccessService teacherContentAccessService;
    private final TeacherLessonService teacherLessonService;
    private final FlashcardRepository flashcardRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final LearningActivityMapper learningActivityMapper;
    private final LearningPayloadCodec learningPayloadCodec;
    private final AuditLogService auditLogService;

    public TeacherLearningActivityService(
            TeacherContentAccessService teacherContentAccessService,
            TeacherLessonService teacherLessonService,
            FlashcardRepository flashcardRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            LearningActivityMapper learningActivityMapper,
            LearningPayloadCodec learningPayloadCodec,
            AuditLogService auditLogService) {
        this.teacherContentAccessService = teacherContentAccessService;
        this.teacherLessonService = teacherLessonService;
        this.flashcardRepository = flashcardRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.learningActivityMapper = learningActivityMapper;
        this.learningPayloadCodec = learningPayloadCodec;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<FlashcardOptionResponse> getFlashcards(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return flashcardRepository.findByLessonIdOrderBySortOrderAscCreatedAtAsc(lesson.getId()).stream()
                .filter(card -> card.getContentStatus() != ContentStatus.ARCHIVED)
                .map(learningActivityMapper::toFlashcardResponse)
                .toList();
    }

    @Transactional
    public FlashcardOptionResponse createFlashcard(SecurityUserDetails currentUser, Long lessonId, CreateFlashcardRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        Flashcard flashcard = new Flashcard();
        flashcard.setLesson(lesson);
        applyFlashcard(flashcard, request.chineseText(), request.pinyin(), request.englishMeaning(), request.malayMeaning(),
                request.imageUrl(), request.audioUrl(), request.extraPayload(), request.displayOrder());
        flashcard.setContentStatus(lesson.getContentStatus());
        Flashcard saved = flashcardRepository.save(flashcard);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_FLASHCARD_CREATE", "Flashcard", saved.getId(),
                "Teacher created a flashcard.");
        return learningActivityMapper.toFlashcardResponse(saved);
    }

    @Transactional
    public FlashcardOptionResponse updateFlashcard(SecurityUserDetails currentUser, Long flashcardId, UpdateFlashcardRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Flashcard flashcard = flashcardRepository.findByIdAndLessonTeacherUserId(flashcardId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested flashcard was not found."));
        teacherContentAccessService.validateEditableStatus(flashcard.getLesson().getContentStatus(), "Lesson");

        applyFlashcard(flashcard, request.chineseText(), request.pinyin(), request.englishMeaning(), request.malayMeaning(),
                request.imageUrl(), request.audioUrl(), request.extraPayload(), request.displayOrder());
        Flashcard saved = flashcardRepository.save(flashcard);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_FLASHCARD_UPDATE", "Flashcard", saved.getId(),
                "Teacher updated a flashcard.");
        return learningActivityMapper.toFlashcardResponse(saved);
    }

    @Transactional
    public void deleteFlashcard(SecurityUserDetails currentUser, Long flashcardId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Flashcard flashcard = flashcardRepository.findByIdAndLessonTeacherUserId(flashcardId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested flashcard was not found."));
        teacherContentAccessService.validateEditableStatus(flashcard.getLesson().getContentStatus(), "Lesson");
        flashcard.setContentStatus(ContentStatus.ARCHIVED);
        flashcardRepository.save(flashcard);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_FLASHCARD_ARCHIVE", "Flashcard", flashcard.getId(),
                "Teacher archived a flashcard.");
    }

    @Transactional(readOnly = true)
    public List<TeacherQuizResponse> getQuizzes(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return quizRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(quiz -> quiz.getContentStatus() != ContentStatus.ARCHIVED)
                .map(this::toTeacherQuizResponse)
                .toList();
    }

    @Transactional
    public TeacherQuizResponse createQuiz(SecurityUserDetails currentUser, Long lessonId, CreateQuizRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        Quiz quiz = new Quiz();
        quiz.setLesson(lesson);
        quiz.setTeacher(teacherProfile);
        quiz.setTitle(request.title().trim());
        quiz.setDescription(teacherContentAccessService.trimToNull(request.description()));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        quiz.setContentStatus(lesson.getContentStatus());
        quiz.setTotalPoints(0);

        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_CREATE", "Quiz", saved.getId(),
                "Teacher created a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public TeacherQuizResponse updateQuiz(SecurityUserDetails currentUser, Long quizId, UpdateQuizRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");

        quiz.setTitle(request.title().trim());
        quiz.setDescription(teacherContentAccessService.trimToNull(request.description()));
        quiz.setTimeLimitMinutes(request.timeLimitMinutes());
        Quiz saved = quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_UPDATE", "Quiz", saved.getId(),
                "Teacher updated a quiz.");
        return toTeacherQuizResponse(saved);
    }

    @Transactional
    public void deleteQuiz(SecurityUserDetails currentUser, Long quizId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");
        quiz.setContentStatus(ContentStatus.ARCHIVED);
        quizRepository.save(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_ARCHIVE", "Quiz", quiz.getId(),
                "Teacher archived a quiz.");
    }

    @Transactional
    public TeacherQuizResponse addQuizQuestion(SecurityUserDetails currentUser, Long quizId, CreateQuizQuestionRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        teacherContentAccessService.validateEditableStatus(quiz.getLesson().getContentStatus(), "Lesson");
        applyQuizQuestion(createQuestion(quiz), request.questionType(), request.questionText(), request.correctAnswerText(),
                request.points(), request.displayOrder(), request.options(), request.explanation());
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_CREATE", "Quiz", quiz.getId(),
                "Teacher added a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional
    public TeacherQuizResponse updateQuizQuestion(SecurityUserDetails currentUser, Long questionId, UpdateQuizQuestionRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = quizQuestionRepository.findByIdAndQuizTeacherUserId(questionId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz question was not found."));
        teacherContentAccessService.validateEditableStatus(question.getQuiz().getLesson().getContentStatus(), "Lesson");
        applyQuizQuestion(question, request.questionType(), request.questionText(), request.correctAnswerText(), request.points(),
                request.displayOrder(), request.options(), request.explanation());
        recomputeQuizTotalPoints(question.getQuiz());
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_UPDATE", "QuizQuestion", question.getId(),
                "Teacher updated a quiz question.");
        return toTeacherQuizResponse(question.getQuiz());
    }

    @Transactional
    public TeacherQuizResponse deleteQuizQuestion(SecurityUserDetails currentUser, Long questionId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        QuizQuestion question = quizQuestionRepository.findByIdAndQuizTeacherUserId(questionId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz question was not found."));
        teacherContentAccessService.validateEditableStatus(question.getQuiz().getLesson().getContentStatus(), "Lesson");
        Quiz quiz = question.getQuiz();
        quizOptionRepository.deleteByQuestionId(question.getId());
        quizQuestionRepository.delete(question);
        recomputeQuizTotalPoints(quiz);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_QUIZ_QUESTION_DELETE", "QuizQuestion", questionId,
                "Teacher deleted a quiz question.");
        return toTeacherQuizResponse(quiz);
    }

    @Transactional(readOnly = true)
    public List<TeacherQuizAttemptSummaryResponse> getQuizAttempts(SecurityUserDetails currentUser, Long quizId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Quiz quiz = findOwnedQuiz(currentUser, quizId);
        return quizAttemptRepository.findByQuizIdOrderByCompletedAtDescCreatedAtDesc(quiz.getId()).stream()
                .filter(QuizAttempt::isSubmitted)
                .map(attempt -> new TeacherQuizAttemptSummaryResponse(
                        attempt.getId(),
                        attempt.getStudent().getId(),
                        attempt.getStudent().getUser() != null
                                ? attempt.getStudent().getUser().getDisplayName()
                                : "Student",
                        attempt.getScore(),
                        attempt.getMaxScore(),
                        attempt.getCompletedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchingGameResponse> getMatchingGames(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return matchingGameRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(game -> game.getContentStatus() != ContentStatus.ARCHIVED)
                .map(this::toMatchingGameResponse)
                .toList();
    }

    @Transactional
    public MatchingGameResponse createMatchingGame(SecurityUserDetails currentUser, Long lessonId, CreateMatchingGameRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);
        MatchingGame game = new MatchingGame();
        game.setLesson(lesson);
        game.setTitle(request.title().trim());
        game.setContentStatus(lesson.getContentStatus());
        MatchingGame saved = matchingGameRepository.save(game);
        replaceMatchingItems(saved, request.items());
        auditLogService.record(teacherProfile.getUser(), "TEACHER_MATCHING_GAME_CREATE", "MatchingGame", saved.getId(),
                "Teacher created a matching game.");
        return toMatchingGameResponse(saved);
    }

    @Transactional
    public MatchingGameResponse updateMatchingGame(SecurityUserDetails currentUser, Long gameId, UpdateMatchingGameRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        MatchingGame game = matchingGameRepository.findByIdAndLessonTeacherUserId(gameId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested matching game was not found."));
        teacherContentAccessService.validateEditableStatus(game.getLesson().getContentStatus(), "Lesson");
        game.setTitle(request.title().trim());
        MatchingGame saved = matchingGameRepository.save(game);
        replaceMatchingItems(saved, request.items());
        auditLogService.record(teacherProfile.getUser(), "TEACHER_MATCHING_GAME_UPDATE", "MatchingGame", saved.getId(),
                "Teacher updated a matching game.");
        return toMatchingGameResponse(saved);
    }

    @Transactional
    public void deleteMatchingGame(SecurityUserDetails currentUser, Long gameId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        MatchingGame game = matchingGameRepository.findByIdAndLessonTeacherUserId(gameId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested matching game was not found."));
        teacherContentAccessService.validateEditableStatus(game.getLesson().getContentStatus(), "Lesson");
        game.setContentStatus(ContentStatus.ARCHIVED);
        matchingGameRepository.save(game);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_MATCHING_GAME_ARCHIVE", "MatchingGame", game.getId(),
                "Teacher archived a matching game.");
    }

    @Transactional(readOnly = true)
    public List<ListeningExerciseResponse> getListeningExercises(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return listeningExerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(exercise -> exercise.getContentStatus() != ContentStatus.ARCHIVED)
                .map(this::toListeningExerciseResponse)
                .toList();
    }

    @Transactional
    public ListeningExerciseResponse createListeningExercise(
            SecurityUserDetails currentUser,
            Long lessonId,
            CreateListeningExerciseRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);
        validateListeningOptions(request.options());

        ListeningExercise exercise = new ListeningExercise();
        exercise.setLesson(lesson);
        exercise.setTitle(request.title().trim());
        exercise.setAudioUrl(teacherContentAccessService.trimToNull(request.audioUrl()));
        exercise.setQuestionPayload(learningPayloadCodec.serializeListeningPayload(
                request.chineseText().trim(),
                teacherContentAccessService.trimToNull(request.pinyin()),
                teacherContentAccessService.trimToNull(request.explanation()),
                request.options()));
        exercise.setContentStatus(lesson.getContentStatus());

        ListeningExercise saved = listeningExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_LISTENING_CREATE", "ListeningExercise", saved.getId(),
                "Teacher created a listening exercise.");
        return toListeningExerciseResponse(saved);
    }

    @Transactional
    public ListeningExerciseResponse updateListeningExercise(
            SecurityUserDetails currentUser,
            Long exerciseId,
            UpdateListeningExerciseRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        ListeningExercise exercise = listeningExerciseRepository.findByIdAndLessonTeacherUserId(exerciseId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested listening exercise was not found."));
        teacherContentAccessService.validateEditableStatus(exercise.getLesson().getContentStatus(), "Lesson");
        validateListeningOptions(request.options());

        exercise.setTitle(request.title().trim());
        exercise.setAudioUrl(teacherContentAccessService.trimToNull(request.audioUrl()));
        exercise.setQuestionPayload(learningPayloadCodec.serializeListeningPayload(
                request.chineseText().trim(),
                teacherContentAccessService.trimToNull(request.pinyin()),
                teacherContentAccessService.trimToNull(request.explanation()),
                request.options()));
        ListeningExercise saved = listeningExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_LISTENING_UPDATE", "ListeningExercise", saved.getId(),
                "Teacher updated a listening exercise.");
        return toListeningExerciseResponse(saved);
    }

    @Transactional
    public void deleteListeningExercise(SecurityUserDetails currentUser, Long exerciseId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        ListeningExercise exercise = listeningExerciseRepository.findByIdAndLessonTeacherUserId(exerciseId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested listening exercise was not found."));
        teacherContentAccessService.validateEditableStatus(exercise.getLesson().getContentStatus(), "Lesson");
        exercise.setContentStatus(ContentStatus.ARCHIVED);
        listeningExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_LISTENING_ARCHIVE", "ListeningExercise", exercise.getId(),
                "Teacher archived a listening exercise.");
    }

    @Transactional(readOnly = true)
    public List<TracingExerciseResponse> getTracingExercises(SecurityUserDetails currentUser, Long lessonId) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        return tracingExerciseRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).stream()
                .filter(exercise -> exercise.getContentStatus() != ContentStatus.ARCHIVED)
                .map(learningActivityMapper::toTracingExerciseResponse)
                .toList();
    }

    @Transactional
    public TracingExerciseResponse createTracingExercise(
            SecurityUserDetails currentUser,
            Long lessonId,
            CreateTracingExerciseRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        Lesson lesson = requireEditableOwnedLesson(currentUser, lessonId);

        TracingExercise exercise = new TracingExercise();
        exercise.setLesson(lesson);
        applyTracing(exercise, request.title(), request.targetCharacter(), request.pinyin(), request.meaning(),
                request.worksheetUrl(), request.instructions());
        exercise.setContentStatus(lesson.getContentStatus());
        TracingExercise saved = tracingExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_TRACING_CREATE", "TracingExercise", saved.getId(),
                "Teacher created a tracing exercise.");
        return learningActivityMapper.toTracingExerciseResponse(saved);
    }

    @Transactional
    public TracingExerciseResponse updateTracingExercise(
            SecurityUserDetails currentUser,
            Long exerciseId,
            UpdateTracingExerciseRequest request) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        TracingExercise exercise = tracingExerciseRepository.findByIdAndLessonTeacherUserId(exerciseId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested tracing exercise was not found."));
        teacherContentAccessService.validateEditableStatus(exercise.getLesson().getContentStatus(), "Lesson");
        applyTracing(exercise, request.title(), request.targetCharacter(), request.pinyin(), request.meaning(),
                request.worksheetUrl(), request.instructions());
        TracingExercise saved = tracingExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_TRACING_UPDATE", "TracingExercise", saved.getId(),
                "Teacher updated a tracing exercise.");
        return learningActivityMapper.toTracingExerciseResponse(saved);
    }

    @Transactional
    public void deleteTracingExercise(SecurityUserDetails currentUser, Long exerciseId) {
        TeacherProfile teacherProfile = teacherContentAccessService.requireApprovedTeacher(currentUser);
        TracingExercise exercise = tracingExerciseRepository.findByIdAndLessonTeacherUserId(exerciseId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested tracing exercise was not found."));
        teacherContentAccessService.validateEditableStatus(exercise.getLesson().getContentStatus(), "Lesson");
        exercise.setContentStatus(ContentStatus.ARCHIVED);
        tracingExerciseRepository.save(exercise);
        auditLogService.record(teacherProfile.getUser(), "TEACHER_TRACING_ARCHIVE", "TracingExercise", exercise.getId(),
                "Teacher archived a tracing exercise.");
    }

    private Lesson requireEditableOwnedLesson(SecurityUserDetails currentUser, Long lessonId) {
        Lesson lesson = teacherLessonService.findOwnedLesson(currentUser, lessonId);
        teacherContentAccessService.validateEditableStatus(lesson.getContentStatus(), "Lesson");
        return lesson;
    }

    private Quiz findOwnedQuiz(SecurityUserDetails currentUser, Long quizId) {
        return quizRepository.findByIdAndTeacherUserId(quizId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Requested quiz was not found."));
    }

    private void applyFlashcard(
            Flashcard flashcard,
            String chineseText,
            String pinyin,
            String englishMeaning,
            String malayMeaning,
            String imageUrl,
            String audioUrl,
            String extraPayload,
            Integer displayOrder) {
        flashcard.setFrontText(chineseText.trim());
        flashcard.setBackText(englishMeaning.trim());
        flashcard.setPinyin(teacherContentAccessService.trimToNull(pinyin));
        flashcard.setEnglishMeaning(englishMeaning.trim());
        flashcard.setMalayMeaning(teacherContentAccessService.trimToNull(malayMeaning));
        flashcard.setImageUrl(teacherContentAccessService.trimToNull(imageUrl));
        flashcard.setAudioUrl(teacherContentAccessService.trimToNull(audioUrl));
        flashcard.setExtraPayload(teacherContentAccessService.trimToNull(extraPayload));
        flashcard.setSortOrder(displayOrder == null ? 0 : displayOrder);
    }

    private QuizQuestion createQuestion(Quiz quiz) {
        QuizQuestion question = new QuizQuestion();
        question.setQuiz(quiz);
        return question;
    }

    private void applyQuizQuestion(
            QuizQuestion question,
            String questionTypeRaw,
            String questionText,
            String correctAnswerText,
            Integer points,
            Integer displayOrder,
            List<com.example.aichinese.dto.learning.QuizOptionRequest> options,
            String explanation) {
        QuizQuestionType questionType = QuizQuestionType.fromValue(questionTypeRaw);
        question.setQuestionType(questionType.name());
        question.setQuestionText(questionText.trim());
        question.setPoints(points);
        question.setSortOrder(displayOrder == null ? 0 : displayOrder);
        question.setExplanation(teacherContentAccessService.trimToNull(explanation));

        List<com.example.aichinese.dto.learning.QuizOptionRequest> safeOptions = options == null ? List.of() : options;
        if (questionType.isOptionBased()) {
            if (safeOptions.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "At least one answer option is required.");
            }
            long correctCount = safeOptions.stream().filter(com.example.aichinese.dto.learning.QuizOptionRequest::correctOption).count();
            if (correctCount != 1) {
                throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct answer option is required.");
            }
            question.setCorrectAnswerText(safeOptions.stream()
                    .filter(com.example.aichinese.dto.learning.QuizOptionRequest::correctOption)
                    .findFirst()
                    .map(com.example.aichinese.dto.learning.QuizOptionRequest::optionText)
                    .map(String::trim)
                    .orElseThrow());
        } else if (correctAnswerText == null || correctAnswerText.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Correct answer text is required for this question type.");
        } else {
            question.setCorrectAnswerText(correctAnswerText.trim());
        }

        QuizQuestion savedQuestion = quizQuestionRepository.save(question);
        quizOptionRepository.deleteByQuestionId(savedQuestion.getId());
        if (!safeOptions.isEmpty()) {
            safeOptions.forEach(option -> {
                QuizOption quizOption = new QuizOption();
                quizOption.setQuestion(savedQuestion);
                quizOption.setOptionText(option.optionText().trim());
                quizOption.setCorrectOption(option.correctOption());
                quizOption.setSortOrder(option.displayOrder() == null ? 0 : option.displayOrder());
                quizOptionRepository.save(quizOption);
            });
        }
    }

    private void recomputeQuizTotalPoints(Quiz quiz) {
        int totalPoints = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId()).stream()
                .map(QuizQuestion::getPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        quiz.setTotalPoints(totalPoints);
        quizRepository.save(quiz);
    }

    private TeacherQuizResponse toTeacherQuizResponse(Quiz quiz) {
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quiz.getId());
        List<QuizOption> options = questions.stream()
                .flatMap(question -> quizOptionRepository.findByQuestionIdOrderBySortOrderAscCreatedAtAsc(question.getId()).stream())
                .toList();
        return learningActivityMapper.toTeacherQuizResponse(
                quiz,
                questions,
                learningActivityMapper.groupOptionsByQuestionId(options));
    }

    private void replaceMatchingItems(MatchingGame game, List<com.example.aichinese.dto.learning.MatchingGameItemRequest> items) {
        matchingGameItemRepository.deleteByMatchingGameId(game.getId());
        items.forEach(item -> {
            MatchingGameItem matchingItem = new MatchingGameItem();
            matchingItem.setMatchingGame(game);
            matchingItem.setLeftText(item.leftText().trim());
            matchingItem.setRightText(item.rightText().trim());
            matchingItem.setLeftImageUrl(teacherContentAccessService.trimToNull(item.leftImageUrl()));
            matchingItem.setRightImageUrl(teacherContentAccessService.trimToNull(item.rightImageUrl()));
            matchingItem.setPinyin(teacherContentAccessService.trimToNull(item.pinyin()));
            matchingItem.setSortOrder(item.displayOrder() == null ? 0 : item.displayOrder());
            matchingGameItemRepository.save(matchingItem);
        });
    }

    private MatchingGameResponse toMatchingGameResponse(MatchingGame game) {
        return learningActivityMapper.toMatchingGameResponse(
                game,
                matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(game.getId()));
    }

    private void validateListeningOptions(List<com.example.aichinese.dto.learning.ListeningOptionRequest> options) {
        if (options == null || options.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one listening option is required.");
        }
        long correctCount = options.stream().filter(com.example.aichinese.dto.learning.ListeningOptionRequest::correctOption).count();
        if (correctCount != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Exactly one correct listening option is required.");
        }
    }

    private ListeningExerciseResponse toListeningExerciseResponse(ListeningExercise exercise) {
        return learningActivityMapper.toListeningExerciseResponse(
                exercise,
                learningPayloadCodec.deserializeListeningPayload(exercise.getQuestionPayload()));
    }

    private void applyTracing(
            TracingExercise exercise,
            String title,
            String targetCharacter,
            String pinyin,
            String meaning,
            String worksheetUrl,
            String instructions) {
        exercise.setTitle(title.trim());
        exercise.setTargetCharacter(targetCharacter.trim());
        exercise.setPinyin(teacherContentAccessService.trimToNull(pinyin));
        exercise.setMeaning(teacherContentAccessService.trimToNull(meaning));
        exercise.setWorksheetUrl(teacherContentAccessService.trimToNull(worksheetUrl));
        exercise.setInstructions(teacherContentAccessService.trimToNull(instructions));
    }
}
