package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteResponse;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
import com.example.aichinese.dto.learn.TypingCandidatesDto;
import com.example.aichinese.entity.PracticeAnswer;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.AnswerConfidence;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.PracticeAnswerRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.repository.ServedHintRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.PracticeCatalog.Item;
import com.example.aichinese.service.learn.PracticeCatalog.SetContent;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * Practice for students: the published sets per type, a set to play (no answers), instant checks (a wrong answer
 * becomes a mistake) and finished runs. A set's XP is paid the first time every item is right, a flashcard set's the
 * first time it is reviewed; a flashcard marked "Still learning" becomes a mistake.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    /** A flashcard's ratings, sent as its answer when the set is finished. */
    private static final String GOT_IT = "GOT_IT";
    private static final String STILL_LEARNING = "STILL_LEARNING";
    /** What Practice's served hints are recorded under, before the item's mistake key. */
    private static final String HINT_REF = "PRACTICE#";
    /** Before every answer: the bound for an item never answered. */
    private static final LocalDateTime NEVER = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PracticeCatalog catalog;
    private final PracticeSetDirectory directory;
    private final PracticeCompletionRepository completionRepository;
    private final PracticeAnswerRepository answerRepository;
    private final ServedHintRepository servedHintRepository;
    private final MistakeService mistakeService;
    private final XpService xpService;
    private final HintPolicyService hintPolicyService;
    private final StudentLearningAccessService accessService;

    /** In list order (by level), so the set the page opens first and the next set offered follow the list shown. */
    @Transactional(readOnly = true)
    public PracticeSetsResponse sets(SecurityUserDetails currentUser, PracticeType type) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return new PracticeSetsResponse(directory.studentSets(type, student.getId()));
    }

    @Transactional(readOnly = true)
    public PracticeSetResponse set(SecurityUserDetails currentUser, PracticeType type, Long id) {
        StudentProfile student = accessService.requireStudent(currentUser);
        PracticeActivityContent set = published(type, id);
        SetContent content = playableContent(type, set);
        List<String> matchingChoices = type == PracticeType.MATCHING
                ? PracticeCatalog.shuffled(content.items().stream().map(item -> item.data().meaning()).toList(), set.getId())
                : List.of();
        HintPolicyService.Run run = hintPolicyService.startRun(student, hintScope(type, set));
        return new PracticeSetResponse(
                set.getId(),
                type,
                set.getLearningStage(),
                set.getXpReward(),
                completionRepository.hasSuccessfulRun(student.getId(), type, id),
                content.items().stream().map(item -> view(type, item)).toList(),
                matchingChoices,
                content.strokeCount(),
                content.characterStrokes(),
                run.startedAt(),
                run.signature());
    }

    /**
     * Instant feedback on one answer; a wrong answer is kept as a mistake, at the top of the list when the student
     * said "Sure". Every check is logged with its confidence and hint steps for the Progress page. The hint steps are
     * the ones the server gave for the item since its last checked answer (each appearance of an item is answered
     * once), whatever the request says: an answer after a hint counts as a guess. A hint the student opened in a run
     * left before answering still counts for the item's next answer. A Listening skip whose audio did not play
     * ({@link #isUncountedSkip}) only shows the answer: no mistake and nothing logged, so it is like an
     * item left unanswered (a hint opened for it counts for its next answer).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AnswerCheckResponse check(
            SecurityUserDetails currentUser, PracticeType type, Long id, PracticeItemAnswer answer) {
        StudentProfile student = accessService.requireStudent(currentUser);
        PracticeActivityContent set = published(type, id);
        if (type == PracticeType.FLASHCARDS || type == PracticeType.TRACING) {
            throw new ResponseStatusException(BAD_REQUEST, "This activity has no answers to check.");
        }
        Item item = item(playableContent(type, set), answer.itemKey());
        AnswerCheckResponse result = item.gradable().check(answer.answer());
        if (isUncountedSkip(type, answer)) {
            return result;
        }
        // Serialize with PracticeService.hint() on the student row so an earlier hint cannot race with this check.
        StudentProfile locked = accessService.lockProfile(student);
        String itemRef = mistakeRef(type, set, item);
        LocalDateTime lastAnswered = answerRepository.findLastAnsweredAt(locked.getId(), itemRef);
        int hintSteps = servedHintRepository.findHighestStepAfter(locked.getId(), HINT_REF + itemRef,
                lastAnswered == null ? NEVER : lastAnswered);
        AnswerConfidence confidence = type.asksConfidence() ? confidenceAfter(answer, hintSteps) : null;
        if (!result.correct()) {
            mistakeService.recordLocked(locked, MistakeSource.PRACTICE, itemRef, set.getTitle(),
                    item.gradable(), answer.answer(), confidence == AnswerConfidence.SURE);
        }
        PracticeAnswer logged = new PracticeAnswer();
        logged.setStudent(locked);
        logged.setPracticeType(type);
        logged.setItemRef(itemRef);
        logged.setCorrect(result.correct());
        logged.setConfidence(confidence);
        logged.setHintSteps(hintSteps);
        answerRepository.save(logged);
        return result.withEffectiveConfidence(confidence);
    }

    /**
     * The one hint for an item, as much as the student's level gives. Hints are refused when the student turned them
     * off, and for one more item once the run has used its level's hints per set; a flashcard set has none. Every hint
     * given is recorded ({@link HintPolicyService#hintInRun}), so the limit and the guess rule of {@link #check} count
     * what the server served. A Matching board is one question with one hint: the pair of one word
     * ({@link HintPolicyService#pairInRun}).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step hint(
            SecurityUserDetails currentUser, PracticeType type, Long id, HintDto.Request request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        if (type == PracticeType.FLASHCARDS) {
            hintPolicyService.requireHintsOn(student);
            throw new ResponseStatusException(BAD_REQUEST, "There is no further hint for this item.");
        }
        PracticeActivityContent set = published(type, id);
        SetContent content = playableContent(type, set);
        Item item = item(content, request.itemKey());
        HintPolicyService.Run run = new HintPolicyService.Run(request.runStartedAt(), request.runSignature());
        List<HintDto.Step> ladder = switch (type) {
            case FLASHCARDS -> List.of();
            case TRACING -> HintBuilder.tracingLadder(item.data().hanzi(), content.strokeCount());
            case MATCHING -> HintBuilder.pairLadder(item.gradable());
            case TYPING -> HintBuilder.typingLadder(item.gradable());
            case LISTENING, DRAG_DROP -> HintBuilder.ladder(item.gradable());
        };
        String scope = hintScope(type, set);
        String itemRef = scope + item.key();
        if (type == PracticeType.MATCHING) {
            return hintPolicyService.pairInRun(student, scope, itemRef, run, ladder);
        }
        // Scope duplicate-row suppression to after the item's last checked answer (read after locking inside serveInRun)
        // so retries ("Once more, to make sure") record a fresh post-answer hint for hintSteps and GUESS enforcement.
        String ref = mistakeRef(type, set, item);
        return hintPolicyService.hintInRun(student, scope, itemRef, run, ladder,
                studentId -> answerRepository.findLastAnsweredAt(studentId, ref));
    }

    /**
     * Typing: the words the typed pinyin spells. Like a keyboard, it offers words only once the pinyin of a word is
     * complete; the answer is one of them, never marked.
     */
    @Transactional(readOnly = true)
    public TypingCandidatesDto.Response candidates(
            SecurityUserDetails currentUser, Long id, TypingCandidatesDto.Request request) {
        accessService.requireStudent(currentUser);
        PracticeActivityContent set = published(PracticeType.TYPING, id);
        Item item = item(playableContent(PracticeType.TYPING, set), request.itemKey());
        String typed = Pinyin.typed(request.pinyin());
        if (typed.isEmpty() || !typed.equals(Pinyin.typed(item.data().pinyin()))) {
            return new TypingCandidatesDto.Response(List.of());
        }
        return new TypingCandidatesDto.Response(item.gradable().options().stream().map(ChoiceOption::text).toList());
    }

    /**
     * Grades a finished run and records it; XP only the first time every item is right. A flashcard set is a review:
     * each card's answer is the learner's rating, "Got it" counts as right, a card still being learned becomes a
     * mistake (asked in the Mistakes review as what the word means), and the first finished review pays the set's XP
     * whatever the ratings.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PracticeCompleteResponse complete(
            SecurityUserDetails currentUser, PracticeType type, Long id, PracticeCompleteRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        PracticeActivityContent set = published(type, id);
        SetContent content = playableContent(type, set);
        Map<String, String> answers =
                LearnAnswers.firstAnswers(request.answers(), PracticeItemAnswer::itemKey, PracticeItemAnswer::answer);
        validateRunAnswers(type, content, answers, request.strokeCount());

        int itemCount = content.items().size();
        int correctCount = countCorrect(type, content, answers, request.strokeCount());
        boolean allCorrect = correctCount == itemCount;

        XpService.XpSession session = xpService.begin(student);
        boolean finishedSet = allCorrect || type == PracticeType.FLASHCARDS;
        if (finishedSet) {
            session.award(type.referenceType(), set.getId(), set.getXpReward(), set.getTitle());
        }
        if (type == PracticeType.FLASHCARDS) {
            List<MistakeService.StillLearningEntry> stillLearning = content.items().stream()
                    .filter(card -> STILL_LEARNING.equals(answers.get(card.key())))
                    .map(card -> new MistakeService.StillLearningEntry(
                            mistakeRef(type, set, card), PracticeCatalog.flashcardQuestion(content, card)))
                    .toList();
            mistakeService.recordStillLearningBatchLocked(session.student(), set.getTitle(), stillLearning);
        }
        PracticeCompletion completion = new PracticeCompletion();
        completion.setStudent(session.student());
        completion.setPracticeType(type);
        completion.setActivityId(set.getId());
        completion.setCorrectCount(correctCount);
        completion.setItemCount(itemCount);
        completion.setCreatedAt(session.sessionTimestamp());
        completion.setUpdatedAt(session.sessionTimestamp());
        completionRepository.saveAndFlush(completion);
        if (finishedSet) {
            session.finishedLessonOrSet();
        }

        PracticeCompleteResponse.NextSet nextSet = directory.nextSet(type, student.getId(), set).orElse(null);
        return new PracticeCompleteResponse(type, correctCount, itemCount, allCorrect, nextSet, session.finish());
    }

    private static void validateRunAnswers(
            PracticeType type, SetContent content, Map<String, String> answers, Integer strokeCount) {
        if (type == PracticeType.TRACING) {
            if (strokeCount == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Draw the character before you finish.");
            }
            return;
        }
        boolean allPresent = content.items().stream().allMatch(item -> answers.containsKey(item.key()));
        if (type == PracticeType.FLASHCARDS && (!allPresent || content.items().stream()
                .map(item -> answers.get(item.key()))
                .anyMatch(rating -> !GOT_IT.equals(rating) && !STILL_LEARNING.equals(rating)))) {
            throw new ResponseStatusException(BAD_REQUEST, "Rate each card Got it or Still learning.");
        }
        if (!allPresent) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer or skip every item before you finish the set.");
        }
    }

    private static int countCorrect(
            PracticeType type, SetContent content, Map<String, String> answers, Integer strokeCount) {
        int correctCount = 0;
        for (Item item : content.items()) {
            boolean correct = switch (type) {
                case FLASHCARDS -> GOT_IT.equals(answers.get(item.key()));
                case TRACING -> strokeCount >= content.strokeCount();
                default -> item.gradable().isCorrect(answers.get(item.key()));
            };
            if (correct) {
                correctCount++;
            }
        }
        return correctCount;
    }

    private static AnswerConfidence confidenceAfter(PracticeItemAnswer answer, int hintSteps) {
        if (TextUtils.isBlankOrSpaces(answer.answer())) {
            return null;
        }
        return hintSteps > 0 ? AnswerConfidence.GUESS : answer.confidence();
    }

    private static boolean isUncountedSkip(PracticeType type, PracticeItemAnswer answer) {
        return answer.audioFailed() && type == PracticeType.LISTENING && TextUtils.isBlankOrSpaces(answer.answer());
    }

    private static Item item(SetContent content, String itemKey) {
        return content.items().stream()
                .filter(candidate -> candidate.key().equals(itemKey))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This item is not part of the set."));
    }

    private PracticeActivityContent published(PracticeType type, Long id) {
        return directory.find(type, id)
                .filter(set -> set.getContentStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This practice set is not available."));
    }

    private SetContent playableContent(PracticeType type, PracticeActivityContent set) {
        SetContent content = catalog.content(type, set);
        if (!catalog.isPlayable(type, content)) {
            throw new ResponseStatusException(BAD_REQUEST, "This activity has no learning material yet.");
        }
        return content;
    }

    /** The key of a Practice item's waiting mistake (one row per item and student). */
    public static String mistakeRef(PracticeType type, PracticeActivityContent set, Item item) {
        return type.name() + "#" + set.getId() + "#" + item.key();
    }

    /** What a run of a set is signed for, and its items' served hints start with ({@code PRACTICE#LISTENING#3#}). */
    private static String hintScope(PracticeType type, PracticeActivityContent set) {
        return HINT_REF + type.name() + "#" + set.getId() + "#";
    }

    /** What a student may see of an item before answering it. */
    private static PracticeSetResponse.Item view(PracticeType type, Item item) {
        PracticeItemData data = item.data();
        return switch (type) {
            case FLASHCARDS, TRACING -> new PracticeSetResponse.Item(item.key(), null, data.hanzi(), data.pinyin(),
                    data.meaning(), null, data.example(), data.exampleMeaning(), List.of(), List.of());
            case LISTENING -> new PracticeSetResponse.Item(item.key(), data.prompt(), null, null, null,
                    data.audioText(), null, null, item.gradable().options(), List.of());
            case DRAG_DROP -> new PracticeSetResponse.Item(item.key(), null, null, null, data.meaning(), null, null,
                    null, List.of(), item.gradable().tiles());
            case MATCHING -> new PracticeSetResponse.Item(item.key(), null, data.hanzi(), data.pinyin(), null, null,
                    null, null, List.of(), List.of());
            // Only the meaning: the pinyin is what the student types, the characters what they choose.
            case TYPING -> new PracticeSetResponse.Item(item.key(), null, null, null, data.meaning(), null, null, null,
                    List.of(), List.of());
        };
    }
}
