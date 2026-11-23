package com.example.aichinese.service.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.classes.QuizOptionRequest;
import com.example.aichinese.dto.classes.QuizQuestionRequest;
import com.example.aichinese.dto.classes.QuizRequest;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.learn.LearnerNotifications;

import jakarta.persistence.EntityManager;

/** Rules of the admin lesson editor checked before anything is copied or stored. */
class LessonEditorServiceTests {

    private static final long LESSON_ID = 5L;

    private final ClassAuthoringAccess access = mock(ClassAuthoringAccess.class);
    private final ClassContentSupport content = mock(ClassContentSupport.class);
    private final LessonRevisionService revisions = mock(LessonRevisionService.class);
    private final QuizRepository quizRepository = mock(QuizRepository.class);
    private final QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
    private final QuizOptionRepository quizOptionRepository = mock(QuizOptionRepository.class);
    private final FileStorageService fileStorage = mock(FileStorageService.class);
    private final ContentSafetyService safety = mock(ContentSafetyService.class);
    private final LessonActivityEditor activityEditor = new LessonActivityEditor(content, revisions,
            mock(EntityManager.class), mock(FlashcardItemRepository.class), mock(MatchingGameItemRepository.class),
            safety);
    private final LessonQuizEditor quizEditor = new LessonQuizEditor(content, revisions, quizRepository,
            quizQuestionRepository, quizOptionRepository, safety);
    private final LessonEditorService editor = new LessonEditorService(access, content, revisions,
            mock(LessonEditorAssembler.class), activityEditor, quizEditor,
            mock(LessonKeyPhraseRepository.class), mock(RecordedLessonVideoRepository.class), fileStorage, safety,
            mock(LearnerNotifications.class), mock(PlatformTransactionManager.class));
    private final SecurityUserDetails admin = new SecurityUserDetails(adminUser());

    /** Checks that an invalid title is rejected before locking or touching storage. */
    @Test
    void aRefusedVideoTitleStoresNoFile() {
        when(safety.requireSafeText(any(), anyInt(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required."));
        MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> editor.saveVideo(admin, LESSON_ID, "Unsafe title", 30, file))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(fileStorage, access, revisions);
    }

    /** A published lesson with its quiz and no working copy yet: refused before a working copy is built. */
    @Test
    void aSecondQuizIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(quizRepository.existsByLessonId(LESSON_ID)).thenReturn(true);

        assertThatThrownBy(() -> editor.addQuiz(LESSON_ID, new QuizRequest("Second quiz", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getReason()).isEqualTo("This lesson already has a quiz.");
                });
        verify(revisions, never()).createRevision(any());
        verify(quizRepository, never()).save(any());
    }

    /** A published lesson that already has an activity of the requested type is rejected before cloning. */
    @Test
    void aDuplicateActivityIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(content.activities(LESSON_ID, ClassActivityType.FLASHCARDS)).thenReturn(List.of(new Flashcard()));

        assertThatThrownBy(() -> editor.addActivity(admin, LESSON_ID,
                new ClassRequests.Activity("FLASHCARDS", "Cards", null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getReason()).isEqualTo("This lesson already has an activity of this type.");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** Invalid activity inputs (e.g. too few matching pairs) are rejected before cloning a published lesson. */
    @Test
    void invalidActivityInputIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(content.activities(LESSON_ID, ClassActivityType.MATCHING)).thenReturn(List.of());
        when(safety.requireSafeText(any(), anyInt(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClassRequests.Activity tooFewPairs = new ClassRequests.Activity("MATCHING", "Match", null,
                List.of(new ClassRequests.MatchPair("你好", "nǐ hǎo", "hello")), null);
        assertThatThrownBy(() -> editor.addActivity(admin, LESSON_ID, tooFewPairs))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).isEqualTo("A matching activity needs at least 2 pairs.");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** A published lesson with a locked quiz rejects adding a question before creating a working copy. */
    @Test
    void addingAQuestionToALockedQuizIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        Quiz quiz = quizFor(lesson, 88L);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(content.quizzes(LESSON_ID)).thenReturn(List.of(quiz));
        when(revisions.questionsLocked(quiz)).thenReturn(true);

        QuizQuestionRequest request = validChoiceQuestion();
        assertThatThrownBy(() -> editor.addQuestion(LESSON_ID, 88L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).contains("submitted attempts");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** A published lesson with a full 5-question quiz rejects adding a 6th question before cloning. */
    @Test
    void addingAQuestionToAFullQuizIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        Quiz quiz = quizFor(lesson, 88L);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(content.quizzes(LESSON_ID)).thenReturn(List.of(quiz));
        when(quizQuestionRepository.countByQuizId(88L)).thenReturn(5L);

        assertThatThrownBy(() -> editor.addQuestion(LESSON_ID, 88L, validChoiceQuestion()))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).contains("exactly 5 questions");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** Invalid question payloads are rejected before cloning a published lesson on add and update. */
    @Test
    void invalidQuestionPayloadIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        Quiz quiz = quizFor(lesson, 88L);
        QuizQuestion question = questionFor(quiz, 301L);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(content.quizzes(LESSON_ID)).thenReturn(List.of(quiz));
        when(quizQuestionRepository.countByQuizId(88L)).thenReturn(2L);
        when(quizQuestionRepository.findById(301L)).thenReturn(Optional.of(question));
        when(safety.requireSafeText(any(), anyInt(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuizQuestionRequest duplicateOptions = new QuizQuestionRequest(
                "MULTIPLE_CHOICE", "Prompt", null, 2,
                List.of(new QuizOptionRequest("Same", true, null), new QuizOptionRequest("Same", false, null)),
                null, null, null, null, null, null);
        assertThatThrownBy(() -> editor.addQuestion(LESSON_ID, 88L, duplicateOptions))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).isEqualTo("The answers of a question must all be different.");
                });
        assertThatThrownBy(() -> editor.updateQuestion(LESSON_ID, 301L, duplicateOptions))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).isEqualTo("The answers of a question must all be different.");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** Locked published quizzes reject update, keep and delete question edits before cloning. */
    @Test
    void editingOrDeletingQuestionsOfALockedQuizIsRefusedBeforeAPublishedLessonIsCopied() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        Quiz quiz = quizFor(lesson, 88L);
        QuizQuestion question = questionFor(quiz, 301L);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), lesson));
        when(content.revisionOf(lesson)).thenReturn(Optional.empty());
        when(quizQuestionRepository.findById(301L)).thenReturn(Optional.of(question));
        when(revisions.questionsLocked(quiz)).thenReturn(true);

        assertThatThrownBy(() -> editor.updateQuestion(LESSON_ID, 301L, validChoiceQuestion()))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).contains("submitted attempts");
                });
        assertThatThrownBy(() -> editor.keepQuestion(LESSON_ID, 301L))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).contains("submitted attempts");
                });
        assertThatThrownBy(() -> editor.deleteQuestion(LESSON_ID, 301L))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).contains("submitted attempts");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** Draft lessons preserve 409/404 status-code precedence ahead of 400 payload validation. */
    @Test
    void draftLessonsPreserveConflictAndNotFoundPrecedenceBeforePayloadValidation() {
        Lesson draft = lesson(ContentStatus.DRAFT);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.DRAFT), draft));
        when(content.activities(LESSON_ID, ClassActivityType.FLASHCARDS)).thenReturn(List.of(new Flashcard()));
        when(quizRepository.existsByLessonId(LESSON_ID)).thenReturn(true);
        when(safety.requireSafeText(any(), anyInt(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsafe text"));

        ClassRequests.Activity invalidActivity = new ClassRequests.Activity("FLASHCARDS", "", null, null, null);
        assertThatThrownBy(() -> editor.addActivity(admin, LESSON_ID, invalidActivity))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> editor.updateActivity(LESSON_ID, "FLASHCARDS", 999L, invalidActivity))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> editor.addQuiz(LESSON_ID, new QuizRequest("", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure ->
                        assertThat(failure.getStatusCode().value()).isEqualTo(409));
    }

    /** An archived class is read-only: nothing is copied or stored. */
    @Test
    void aLessonOfAnArchivedClassIsNotEdited() {
        Lesson lesson = lesson(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.ARCHIVED), lesson));

        assertThatThrownBy(() -> editor.addQuiz(LESSON_ID, new QuizRequest("Quiz", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getReason()).isEqualTo("Restore the class before you edit it.");
                });
        verify(revisions, never()).createRevision(any());
    }

    /** saveContent enforces 409 archived-lesson/class precedence ahead of unit-move and text validation. */
    @Test
    void saveContentPreservesArchivedPrecedenceBeforeUnitMoveAndSafetyValidation() {
        Lesson archived = lesson(ContentStatus.ARCHIVED);
        LearningModule currentUnit = new LearningModule();
        currentUnit.setId(10L);
        archived.setModule(currentUnit);
        CoursePackage activeClass = classWith(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID)).thenReturn(new ClassAuthoringAccess.LockedLesson(activeClass, archived));
        when(safety.requireSafeText(any(), anyInt(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson title is required."));

        ClassRequests.LessonContent request = new ClassRequests.LessonContent("", 999L, 10, null, List.of());
        assertThatThrownBy(() -> editor.saveContent(admin, LESSON_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getReason()).isEqualTo("Restore the lesson before you edit it.");
                });
        verifyNoInteractions(safety);
    }

    /** Moving a published lesson inside a published class records an audit entry without cloning the lesson. */
    @Test
    void movingAPublishedLessonInAPublishedClassIsAuditedImmediately() {
        Lesson published = lesson(ContentStatus.PUBLISHED);
        published.setTitle("Greetings");
        published.setEstimatedDurationMinutes(10);
        LearningModule unitOne = new LearningModule();
        unitOne.setId(10L);
        unitOne.setTitle("Unit 1");
        LearningModule unitTwo = new LearningModule();
        unitTwo.setId(20L);
        unitTwo.setTitle("Unit 2");
        published.setModule(unitOne);
        CoursePackage liveClass = classWith(ContentStatus.PUBLISHED);
        User adminUser = adminUser();
        when(access.lockLesson(LESSON_ID)).thenReturn(new ClassAuthoringAccess.LockedLesson(liveClass, published));
        when(content.revisionOf(published)).thenReturn(Optional.empty());
        when(content.units(liveClass)).thenReturn(List.of(unitOne, unitTwo));
        when(content.keyPhraseViews(LESSON_ID)).thenReturn(List.of());
        when(content.nextLessonPosition(unitTwo)).thenReturn(3);
        when(safety.requireSafeText(any(), anyInt(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(access.requireAdmin(admin)).thenReturn(adminUser);

        editor.saveContent(admin, LESSON_ID, new ClassRequests.LessonContent("Greetings", 20L, 10, null, List.of()));

        assertThat(published.getModule()).isSameAs(unitTwo);
        assertThat(published.getUnitPosition()).isEqualTo(3);
        verify(revisions, never()).createRevision(any());
        verify(access).audit(adminUser, "ADMIN_LESSON_EDIT", published,
                "Admin moved a published lesson to \"Unit 2\".");
    }

    /** No-op removeVideo, updateActivity and updateQuestion on published lessons do not clone the lesson. */
    @Test
    void noOpEditsOnPublishedLessonsDoNotCreateAWorkingCopy() {
        Lesson published = lesson(ContentStatus.PUBLISHED);
        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), published));
        when(content.revisionOf(published)).thenReturn(Optional.empty());
        when(content.video(LESSON_ID)).thenReturn(Optional.empty());
        when(safety.requireSafeText(any(), anyInt(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        editor.removeVideo(LESSON_ID);

        Flashcard existingCards = new Flashcard();
        existingCards.setId(44L);
        existingCards.setTitle("Cards");
        when(content.activities(LESSON_ID, ClassActivityType.FLASHCARDS)).thenReturn(List.of(existingCards));
        when(content.cardViews(44L)).thenReturn(List.of(new LessonViews.KeyPhrase("你好", "nǐ hǎo", "hello")));
        editor.updateActivity(LESSON_ID, "FLASHCARDS", 44L, new ClassRequests.Activity(
                "FLASHCARDS", "Cards", null, null, List.of(new ClassRequests.Card("你好", "nǐ hǎo", "hello"))));

        Quiz quiz = quizFor(published, 88L);
        QuizQuestion existingQuestion = questionFor(quiz, 301L);
        existingQuestion.setQuestionType(QuizQuestionType.MULTIPLE_CHOICE);
        existingQuestion.setQuestionText("Prompt");
        existingQuestion.setPoints(2);
        existingQuestion.setCorrectAnswerText("A");
        existingQuestion.setAiDraft(false);
        QuizOption optA = new QuizOption();
        optA.setOptionText("A");
        optA.setCorrectOption(true);
        QuizOption optB = new QuizOption();
        optB.setOptionText("B");
        optB.setCorrectOption(false);
        when(quizQuestionRepository.findById(301L)).thenReturn(Optional.of(existingQuestion));
        when(quizOptionRepository.findForQuestions(List.of(existingQuestion)))
                .thenReturn(Map.of(301L, List.of(optA, optB)));
        editor.updateQuestion(LESSON_ID, 301L, validChoiceQuestion());

        verify(revisions, never()).createRevision(any());
    }

    /** Media-link validation for LISTEN_CHOOSE and MATCH_WORD_IMAGE rejects missing, non-HTTP, userinfo and bad ports. */
    @Test
    void mediaQuestionValidationRejectsMissingAndUnsafeLinks() {
        when(safety.requireSafeText(any(), anyInt(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<QuizOptionRequest> options = List.of(
                new QuizOptionRequest("A", true, null), new QuizOptionRequest("B", false, null));

        assertThatThrownBy(() -> quizEditor.validateQuestion(new QuizQuestionRequest(
                "LISTEN_CHOOSE", "Listen", null, 2, options, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getReason()).isEqualTo("A listen-and-choose question needs an audio link."));

        assertThatThrownBy(() -> quizEditor.validateQuestion(new QuizQuestionRequest(
                "MATCH_WORD_IMAGE", "Match", null, 2, options, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getReason()).isEqualTo("A word and image question needs an image link."));

        for (String badLink : List.of("javascript:alert(1)", "https://user:pass@cdn.example/a.mp3", "https://cdn.example:0/a.mp3")) {
            assertThatThrownBy(() -> quizEditor.validateQuestion(new QuizQuestionRequest(
                    "LISTEN_CHOOSE", "Listen", null, 2, options, null, null, badLink, null, null, null)))
                    .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                        assertThat(failure.getStatusCode().value()).isEqualTo(400);
                        assertThat(failure.getReason()).contains("Audio link must be a full http:// or https:// web address");
                    });
        }
    }

    /** Editing a published question id after the revision's quiz diverged returns 409. */
    @Test
    void stalePublishedQuestionIdReturnsConflictWhenRevisionQuizDiverged() {
        Lesson published = lesson(ContentStatus.PUBLISHED);
        Lesson revision = new Lesson();
        revision.setId(50L);
        revision.setRevisionOf(published);
        Quiz publishedQuiz = quizFor(published, 88L);
        Quiz revisedQuiz = quizFor(revision, 99L);
        revisedQuiz.setRevisionSource(publishedQuiz);
        QuizQuestion publishedQuestion = questionFor(publishedQuiz, 301L);

        when(access.lockLesson(LESSON_ID))
                .thenReturn(new ClassAuthoringAccess.LockedLesson(classWith(ContentStatus.PUBLISHED), published));
        when(content.revisionOf(published)).thenReturn(Optional.of(revision));
        when(quizQuestionRepository.findById(301L)).thenReturn(Optional.of(publishedQuestion));
        when(content.quizzes(50L)).thenReturn(List.of(revisedQuiz));
        when(content.questions(88L)).thenReturn(List.of(publishedQuestion));
        when(content.questions(99L)).thenReturn(List.of());
        when(revisions.sameQuestions(List.of(publishedQuestion), List.of())).thenReturn(false);

        assertThatThrownBy(() -> editor.keepQuestion(LESSON_ID, 301L))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(409);
                    assertThat(failure.getReason())
                            .isEqualTo("This quiz has changed. Reload the lesson before editing its questions.");
                });
    }

    /** Publishing is blocked when any key phrase or activity word (flashcards, tracing, matching) is missing pinyin. */
    @Test
    void assemblerRejectsPublishingWhenPinyinIsMissing() {
        LessonEditorAssembler realAssembler = new LessonEditorAssembler(
                content, revisions, quizOptionRepository, new UploadProperties());
        Lesson draft = lesson(ContentStatus.DRAFT);
        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setTitle("Intro");
        when(content.video(LESSON_ID)).thenReturn(Optional.of(video));

        Flashcard flashcard = new Flashcard();
        flashcard.setId(1L);
        TracingExercise tracing = new TracingExercise();
        tracing.setId(2L);
        MatchingGame matching = new MatchingGame();
        matching.setId(3L);
        when(content.activities(LESSON_ID)).thenReturn(List.of(flashcard, tracing, matching));
        Quiz quiz = quizFor(draft, 10L);
        when(content.quizzes(LESSON_ID)).thenReturn(List.of(quiz));
        when(revisions.questionsLocked(quiz)).thenReturn(true);

        var validPhrase = new LessonViews.KeyPhrase("你好", "nǐ hǎo", "hello");
        var missingPhrase = new LessonViews.KeyPhrase("你好", "", "hello");
        var emptyParts = new ClassContentSupport.ActivityParts(List.of(), List.of(), List.of());

        // Missing pinyin on key phrase
        when(content.keyPhraseViews(LESSON_ID)).thenReturn(List.of(missingPhrase));
        when(content.activityParts(any())).thenReturn(emptyParts);
        assertMissingPinyinRejected(realAssembler, draft);

        // Missing pinyin on flashcard, tracing character, or matching pair
        when(content.keyPhraseViews(LESSON_ID)).thenReturn(List.of(validPhrase));
        when(content.activityParts(flashcard))
                .thenReturn(new ClassContentSupport.ActivityParts(List.of(), List.of(), List.of(missingPhrase)));
        when(content.activityParts(tracing)).thenReturn(emptyParts);
        when(content.activityParts(matching)).thenReturn(emptyParts);
        assertMissingPinyinRejected(realAssembler, draft);

        when(content.activityParts(flashcard)).thenReturn(emptyParts);
        when(content.activityParts(tracing)).thenReturn(new ClassContentSupport.ActivityParts(
                List.of(new LessonViews.TraceCharacter("好", " ", "good")), List.of(), List.of()));
        assertMissingPinyinRejected(realAssembler, draft);

        when(content.activityParts(tracing)).thenReturn(emptyParts);
        when(content.activityParts(matching))
                .thenReturn(new ClassContentSupport.ActivityParts(List.of(), List.of(missingPhrase), List.of()));
        assertMissingPinyinRejected(realAssembler, draft);
    }

    private static void assertMissingPinyinRejected(LessonEditorAssembler assembler, Lesson draft) {
        assertThatThrownBy(() -> assembler.requireComplete(draft))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).isEqualTo("Add pinyin to every new word before you publish.");
                });
    }

    private static Lesson lesson(ContentStatus status) {
        Lesson lesson = new Lesson();
        lesson.setId(LESSON_ID);
        lesson.setContentStatus(status);
        return lesson;
    }

    private static Quiz quizFor(Lesson lesson, long quizId) {
        Quiz quiz = new Quiz();
        quiz.setId(quizId);
        quiz.setLesson(lesson);
        return quiz;
    }

    private static QuizQuestion questionFor(Quiz quiz, long questionId) {
        QuizQuestion question = new QuizQuestion();
        question.setId(questionId);
        question.setQuiz(quiz);
        return question;
    }

    private static QuizQuestionRequest validChoiceQuestion() {
        return new QuizQuestionRequest(
                "MULTIPLE_CHOICE", "Prompt", null, 2,
                List.of(new QuizOptionRequest("A", true, null), new QuizOptionRequest("B", false, null)),
                null, null, null, null, null, null);
    }

    private static CoursePackage classWith(ContentStatus status) {
        CoursePackage coursePackage = new CoursePackage();
        coursePackage.setContentStatus(status);
        return coursePackage;
    }

    private static User adminUser() {
        User user = new User();
        user.setId(9L);
        return user;
    }
}
