package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * The practice activities of a class lesson in focus mode: flashcards (the activity's cards, reviewed card by card),
 * character tracing (not graded) and matching (checked on the server, so the answer key never reaches the browser
 * before an answer). Finishing an activity completes a lesson step; classes give no XP.
 */
@Service
@RequiredArgsConstructor
public class StudentClassActivityService {

    private static final String ACTIVITY_NOT_FOUND = "Activity was not found.";

    private final ClassAccessService access;
    private final StudentLearningAccessService studentAccess;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final ClassActivityCompletionRepository completionRepository;

    private record OpenActivity(StudentProfile student, Long classId, Lesson lesson, LessonActivity activity) {
    }

    private OpenActivity open(StudentProfile student, String type, Long activityId) {
        ClassActivityType activityType = ClassActivityType.tryFrom(type)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ACTIVITY_NOT_FOUND));
        LessonActivity activity = content.findActivity(activityType, activityId)
                .filter(found -> found.getLesson() != null)
                .filter(ClassContentSupport::isLive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ACTIVITY_NOT_FOUND));
        ClassAccessService.StudentLesson context = access.requireOpenLesson(student, activity.getLesson().getId());
        return new OpenActivity(student, context.coursePackage().getId(), context.lesson(), activity);
    }

    @Transactional(readOnly = true)
    public LessonViews.ActivityPlay play(SecurityUserDetails currentUser, String type, Long activityId) {
        OpenActivity open = open(studentAccess.requireStudent(currentUser), type, activityId);
        List<LessonActivity> activities = content.activities(open.lesson().getId()).stream()
                .filter(ClassContentSupport::isLive)
                .toList();
        // Entities are equal by type and id; the open activity is one of the lesson's live activities.
        int index = activities.indexOf(open.activity());
        ClassActivityType activityType = ClassContentSupport.typeOf(open.activity());
        List<LessonViews.KeyPhrase> cards = List.of();
        List<LessonViews.TraceCharacter> characters = List.of();
        List<LessonViews.MatchWord> words = List.of();
        List<String> meanings = List.of();
        switch (activityType) {
            case FLASHCARDS -> cards = flashcards(open);
            case TRACING -> characters = TracingCharacters.read((TracingExercise) open.activity());
            case MATCHING -> {
                List<MatchingGameItem> items = content.matchingItems(activityId);
                words = items.stream().map(item -> new LessonViews.MatchWord(item.getId(), item.getLeftText(), item.getPinyin())).toList();
                meanings = new ArrayList<>(items.stream().map(MatchingGameItem::getRightText).toList());
                Collections.shuffle(meanings);
            }
        }
        return new LessonViews.ActivityPlay(
                activityType.name(),
                activityId,
                open.classId(),
                open.lesson().getId(),
                open.activity().getTitle(),
                hintsAllowed(open.lesson()),
                index + 1,
                activities.size(),
                open.lesson().getTitle(),
                index + 1 < activities.size() ? ref(activities.get(index + 1)) : null,
                cards,
                characters,
                words,
                meanings);
    }

    /** Older flashcard activities use the lesson's key phrases. */
    private List<LessonViews.KeyPhrase> flashcards(OpenActivity open) {
        List<LessonViews.KeyPhrase> own = content.cardViews(open.activity().getId());
        return own.isEmpty() ? content.keyPhraseViews(open.lesson().getId()) : own;
    }

    /** Flashcards (every card reviewed) and tracing (not graded) complete when the student says so. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LessonViews.ActivityOutcome complete(SecurityUserDetails currentUser, String type, Long activityId) {
        OpenActivity open = open(studentAccess.requireLockedStudent(currentUser), type, activityId);
        boolean hasMaterial = switch (ClassContentSupport.typeOf(open.activity())) {
            case FLASHCARDS -> !flashcards(open).isEmpty();
            case TRACING -> !TextUtils.isBlankOrSpaces(((TracingExercise) open.activity()).getTargetCharacter());
            case MATCHING -> throw new ResponseStatusException(BAD_REQUEST, "Check your pairs to finish a matching activity.");
        };
        if (!hasMaterial) {
            throw new ResponseStatusException(BAD_REQUEST, "This activity has no learning material yet.");
        }
        return new LessonViews.ActivityOutcome(true, List.of(), record(open).status());
    }

    /** Checks the student's pairs; the activity completes when every pair is right. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LessonViews.ActivityOutcome checkMatching(
            SecurityUserDetails currentUser,
            Long activityId,
            ClassRequests.MatchingAnswer request) {
        OpenActivity open = open(studentAccess.requireLockedStudent(currentUser), ClassActivityType.MATCHING.name(), activityId);
        List<MatchingGameItem> items = content.matchingItems(activityId);
        Map<Long, String> chosen = new HashMap<>();
        for (ClassRequests.ChosenPair pair : request.pairs()) {
            if (chosen.containsKey(pair.wordId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Choose one meaning per word.");
            }
            chosen.put(pair.wordId(), pair.meaning());
        }
        List<LessonViews.PairResult> results = new ArrayList<>();
        boolean allRight = !items.isEmpty();
        for (MatchingGameItem item : items) {
            String meaning = chosen.remove(item.getId());
            boolean correct = meaning != null
                    && TextUtils.normalizeAnswerText(meaning).equals(TextUtils.normalizeAnswerText(item.getRightText()));
            allRight &= correct;
            results.add(new LessonViews.PairResult(item.getId(), correct));
        }
        if (!chosen.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Pair the words of this activity only.");
        }
        ClassProgressService.LessonState state = allRight
                ? record(open)
                : progress.lessonState(open.student(), open.lesson());
        return new LessonViews.ActivityOutcome(allRight, results, state.status());
    }

    /**
     * The meaning of one word, for the matching hint: only in a lesson whose "Allow SparkBot hints" is on (the
     * setting of its quiz).
     */
    @Transactional(readOnly = true)
    public LessonViews.MatchHint matchingHint(SecurityUserDetails currentUser, Long activityId, Long wordId) {
        OpenActivity open = open(studentAccess.requireStudent(currentUser), ClassActivityType.MATCHING.name(), activityId);
        if (!hintsAllowed(open.lesson())) {
            throw new ResponseStatusException(BAD_REQUEST, "Hints are not available in this activity.");
        }
        return content.matchingItems(activityId).stream()
                .filter(item -> item.getId().equals(wordId))
                .findFirst()
                .map(item -> new LessonViews.MatchHint(item.getId(), item.getRightText()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Pair the words of this activity only."));
    }

    /** The lesson's "Allow SparkBot hints": the setting of its published quiz (off without one). */
    private boolean hintsAllowed(Lesson lesson) {
        return content.quizzes(lesson.getId()).stream()
                .filter(ClassContentSupport::isLive)
                .anyMatch(Quiz::isHintsAllowed);
    }

    /** Saves the completion and the progress it earns while the caller holds the learner's lock. */
    private ClassProgressService.LessonState record(OpenActivity open) {
        ClassActivityType type = ClassContentSupport.typeOf(open.activity());
        LocalDateTime now = LocalDateTime.now();
        ClassActivityCompletion completion = completionRepository
                .findByStudentIdAndActivityTypeAndActivityId(open.student().getId(), type, open.activity().getId())
                .orElse(null);
        if (completion == null) {
            completion = new ClassActivityCompletion();
            completion.setStudent(open.student());
            completion.setLesson(open.lesson());
            completion.setActivityType(type);
            completion.setActivityId(open.activity().getId());
        } else {
            completion.setTimesCompleted(completion.getTimesCompleted() + 1);
        }
        completion.setCompletedAt(now);
        completionRepository.save(completion);
        return progress.recordProgress(open.student(), open.lesson());
    }

    private static LessonViews.ActivityRef ref(LessonActivity activity) {
        return new LessonViews.ActivityRef(ClassContentSupport.typeOf(activity).name(), activity.getId());
    }
}
