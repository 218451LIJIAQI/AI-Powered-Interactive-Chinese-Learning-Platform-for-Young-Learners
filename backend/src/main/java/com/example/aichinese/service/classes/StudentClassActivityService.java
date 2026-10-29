package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.ClassActivityCompletion;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.repository.ClassActivityCompletionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.QuizScoring;

/**
 * The practice activities of a class lesson in focus mode: flashcards (the lesson's key phrases, reviewed card by
 * card), character tracing (not graded) and matching (checked on the server, so the answer key never reaches the
 * browser before an answer). Finishing an activity completes a lesson step; classes give no XP.
 */
@Service
public class StudentClassActivityService {

    private static final String ACTIVITY_NOT_FOUND = "Activity was not found.";

    private final ClassAccessService access;
    private final ClassContentSupport content;
    private final ClassProgressService progress;
    private final ClassActivityCompletionRepository completionRepository;

    public StudentClassActivityService(
            ClassAccessService access,
            ClassContentSupport content,
            ClassProgressService progress,
            ClassActivityCompletionRepository completionRepository) {
        this.access = access;
        this.content = content;
        this.progress = progress;
        this.completionRepository = completionRepository;
    }

    private record OpenActivity(StudentProfile student, Lesson lesson, LessonActivity activity) {
    }

    private OpenActivity open(SecurityUserDetails currentUser, String type, Long activityId) {
        StudentProfile student = access.requireStudent(currentUser);
        ClassActivityType activityType = ClassActivityType.tryFrom(type == null ? null : type.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ACTIVITY_NOT_FOUND));
        LessonActivity activity = content.findActivity(activityType, activityId)
                .filter(found -> found.getLesson() != null)
                .filter(ClassContentSupport::isLive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ACTIVITY_NOT_FOUND));
        return new OpenActivity(student, access.requireOpenLesson(student, activity.getLesson().getId()).lesson(), activity);
    }

    @Transactional(readOnly = true)
    public LessonViews.ActivityPlay play(SecurityUserDetails currentUser, String type, Long activityId) {
        OpenActivity open = open(currentUser, type, activityId);
        List<LessonActivity> activities = content.activities(open.lesson().getId()).stream()
                .filter(ClassContentSupport::isLive)
                .toList();
        int index = indexOf(activities, open.activity());
        boolean done = completionRepository.findByStudentIdAndActivityTypeAndActivityId(
                open.student().getId(), ClassContentSupport.typeOf(open.activity()), activityId).isPresent();
        List<LessonViews.KeyPhrase> cards = List.of();
        List<LessonViews.TraceCharacter> characters = List.of();
        List<LessonViews.MatchWord> words = List.of();
        List<String> meanings = List.of();
        switch (ClassContentSupport.typeOf(open.activity())) {
            case FLASHCARDS -> cards = content.keyPhrases(open.lesson().getId()).stream()
                    .map(phrase -> new LessonViews.KeyPhrase(phrase.getChinese(), phrase.getPinyin(), phrase.getMeaning()))
                    .toList();
            case TRACING -> characters = TracingCharacters.read((TracingExercise) open.activity()).stream()
                    .map(entry -> new LessonViews.TraceCharacter(entry.character(), entry.pinyin(), entry.meaning()))
                    .toList();
            case MATCHING -> {
                List<MatchingGameItem> items = content.matchingItems(activityId);
                words = items.stream().map(item -> new LessonViews.MatchWord(item.getId(), item.getLeftText(), item.getPinyin())).toList();
                List<String> shuffled = new ArrayList<>(items.stream().map(MatchingGameItem::getRightText).toList());
                Collections.shuffle(shuffled);
                meanings = shuffled;
            }
        }
        LearningStage stage = ((PracticeActivityContent) open.activity()).getLearningStage();
        return new LessonViews.ActivityPlay(
                ClassContentSupport.typeOf(open.activity()).name(),
                activityId,
                open.activity().getTitle(),
                stage == null ? null : stage.name(),
                index + 1,
                activities.size(),
                open.lesson().getTitle(),
                done,
                index > 0 ? ref(activities.get(index - 1)) : null,
                index + 1 < activities.size() ? ref(activities.get(index + 1)) : null,
                cards,
                characters,
                words,
                meanings);
    }

    /** Flashcards (every card reviewed) and tracing (not graded) complete when the student says so. */
    @Transactional
    public LessonViews.ActivityOutcome complete(SecurityUserDetails currentUser, String type, Long activityId) {
        OpenActivity open = open(currentUser, type, activityId);
        if (ClassContentSupport.typeOf(open.activity()) == ClassActivityType.MATCHING) {
            throw new ResponseStatusException(BAD_REQUEST, "Check your pairs to finish a matching activity.");
        }
        record(open);
        return outcome(open, true, List.of());
    }

    /** Checks the student's pairs; the activity completes when every pair is right. */
    @Transactional
    public LessonViews.ActivityOutcome checkMatching(
            SecurityUserDetails currentUser,
            Long activityId,
            ClassRequests.MatchingAnswer request) {
        OpenActivity open = open(currentUser, ClassActivityType.MATCHING.name(), activityId);
        Map<Long, MatchingGameItem> items = new HashMap<>();
        content.matchingItems(activityId).forEach(item -> items.put(item.getId(), item));
        Map<Long, String> chosen = new HashMap<>();
        for (ClassRequests.ChosenPair pair : request.pairs()) {
            if (!items.containsKey(pair.wordId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Pair the words of this activity only.");
            }
            chosen.put(pair.wordId(), pair.meaning());
        }
        List<LessonViews.PairResult> results = new ArrayList<>();
        boolean allRight = true;
        for (MatchingGameItem item : items.values().stream()
                .sorted((first, second) -> Integer.compare(first.getSortOrder(), second.getSortOrder()))
                .toList()) {
            String meaning = chosen.get(item.getId());
            boolean correct = meaning != null
                    && QuizScoring.normalizeText(meaning).equals(QuizScoring.normalizeText(item.getRightText()));
            allRight &= correct;
            results.add(new LessonViews.PairResult(item.getId(), correct));
        }
        if (allRight) {
            record(open);
        }
        return outcome(open, allRight, results);
    }

    /** The meaning of one word, for the matching hint. The player offers hints by stage; Independent has none. */
    @Transactional(readOnly = true)
    public LessonViews.MatchHint matchingHint(SecurityUserDetails currentUser, Long activityId, Long wordId) {
        OpenActivity open = open(currentUser, ClassActivityType.MATCHING.name(), activityId);
        if (((MatchingGame) open.activity()).getLearningStage() == LearningStage.INDEPENDENT) {
            throw new ResponseStatusException(BAD_REQUEST, "Hints are not available in this activity.");
        }
        return content.matchingItems(activityId).stream()
                .filter(item -> item.getId().equals(wordId))
                .findFirst()
                .map(item -> new LessonViews.MatchHint(item.getId(), item.getRightText()))
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Pair the words of this activity only."));
    }

    private void record(OpenActivity open) {
        ClassActivityType type = ClassContentSupport.typeOf(open.activity());
        LocalDateTime now = LocalDateTime.now();
        ClassActivityCompletion completion = completionRepository
                .findByStudentIdAndActivityTypeAndActivityId(open.student().getId(), type, open.activity().getId())
                .map(existing -> {
                    existing.setTimesCompleted(existing.getTimesCompleted() + 1);
                    existing.setCompletedAt(now);
                    return existing;
                })
                .orElseGet(() -> {
                    ClassActivityCompletion created = new ClassActivityCompletion();
                    created.setStudent(open.student());
                    created.setLesson(open.lesson());
                    created.setActivityType(type);
                    created.setActivityId(open.activity().getId());
                    created.setCompletedAt(now);
                    created.setTimesCompleted(1);
                    return created;
                });
        completionRepository.save(completion);
        progress.recordProgress(open.student(), open.lesson());
    }

    private LessonViews.ActivityOutcome outcome(OpenActivity open, boolean done, List<LessonViews.PairResult> pairs) {
        ClassProgressService.LessonState state = progress.lessonStates(open.student(), List.of(open.lesson()))
                .get(open.lesson().getId());
        return new LessonViews.ActivityOutcome(
                done,
                pairs,
                state == null ? 0 : state.stepsDone(),
                state == null ? 0 : state.stepsTotal(),
                ClassProgressService.status(state));
    }

    private static int indexOf(List<LessonActivity> activities, LessonActivity activity) {
        for (int index = 0; index < activities.size(); index++) {
            LessonActivity candidate = activities.get(index);
            if (candidate.getId().equals(activity.getId())
                    && ClassContentSupport.typeOf(candidate) == ClassContentSupport.typeOf(activity)) {
                return index;
            }
        }
        return 0;
    }

    private static LessonViews.ActivityRef ref(LessonActivity activity) {
        return new LessonViews.ActivityRef(ClassContentSupport.typeOf(activity).name(), activity.getId(), activity.getTitle());
    }
}
