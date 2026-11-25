package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.MistakeListResponse;
import com.example.aichinese.dto.learn.MistakeReviewCompleteRequest;
import com.example.aichinese.dto.learn.MistakeReviewCompleteResponse;
import com.example.aichinese.dto.learn.MistakeReviewResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.dto.learn.QuestionView;
import com.example.aichinese.entity.LearningMistake;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.repository.LearningMistakeRepository.SourceCount;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * Mistakes from Learn and Practice: recorded when a check finds a wrong answer, listed on the Mistakes tab and cleared
 * by the Mistakes review. A review clears the mistakes answered right and earns +10 XP once a day.
 */
@Service
@RequiredArgsConstructor
public class MistakeService {

    /** Questions in one review. */
    private static final int REVIEW_SIZE = 20;
    /**
     * What a review's run is signed for, and its served hints are recorded under with the mistake id
     * ({@code MISTAKES#78}).
     */
    private static final String REVIEW_HINT_REF = "MISTAKES#";

    private final LearningMistakeRepository mistakeRepository;
    private final XpService xpService;
    private final LearnClock clock;
    private final HintPolicyService hintPolicyService;
    private final StudentLearningAccessService accessService;

    /**
     * Keeps a wrong answer for review. The same item answered wrong again updates its waiting row, or reopens it when
     * it was cleared before. {@code sure} (the student said "Sure") puts the mistake at the top of the list. Returns
     * the saved row, which the demo data dates back. The requests that call this run read committed (the check and
     * complete endpoints): the locked lookup of a missing row then locks no index gap, which another learner's first
     * mistake could share, so two learners' first mistakes cannot block or deadlock each other.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LearningMistake record(
            StudentProfile student,
            MistakeSource source,
            String itemRef,
            String sourceTitle,
            GradableItem item,
            String yourAnswer,
            boolean sure) {
        // A first mistake has no row to lock. Serialize its creation with this learner's other answers and reviews.
        StudentProfile locked = accessService.lockProfile(student);
        return recordLocked(locked, source, itemRef, sourceTitle, item, yourAnswer, sure, false);
    }

    /** Records a mistake when the caller already holds {@code locked}'s profile lock in a READ_COMMITTED transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    LearningMistake recordLocked(
            StudentProfile locked,
            MistakeSource source,
            String itemRef,
            String sourceTitle,
            GradableItem item,
            String yourAnswer,
            boolean sure) {
        return recordLocked(locked, source, itemRef, sourceTitle, item, yourAnswer, sure, false);
    }

    private LearningMistake recordLocked(
            StudentProfile locked,
            MistakeSource source,
            String itemRef,
            String sourceTitle,
            GradableItem item,
            String yourAnswer,
            boolean sure,
            boolean stillLearning) {
        LocalDateTime now = clock.serverTimeAt(clock.now());
        LearningMistake mistake = mistakeRepository.findLockedByStudentIdAndItemRef(locked.getId(), itemRef)
                .orElse(null);
        boolean isNew = mistake == null;
        mistake = populateMistake(mistake, locked, source, itemRef, sourceTitle, item, yourAnswer, sure, stillLearning, now);
        return isNew ? mistakeRepository.save(mistake) : mistake;
    }

    private static LearningMistake populateMistake(
            LearningMistake existing,
            StudentProfile locked,
            MistakeSource source,
            String itemRef,
            String sourceTitle,
            GradableItem item,
            String yourAnswer,
            boolean sure,
            boolean stillLearning,
            LocalDateTime now) {
        LearningMistake mistake = existing;
        if (mistake == null) {
            mistake = new LearningMistake();
            mistake.setStudent(locked);
            mistake.setItemRef(itemRef);
        } else {
            mistake.setWrongCount(mistake.getWrongCount() + 1);
        }
        mistake.setMistakeSource(source);
        mistake.setSourceTitle(sourceTitle);
        mistake.setAnswerKind(item.answerKind());
        mistake.setQuestionKind(item.type());
        mistake.setPrompt(item.prompt());
        mistake.setHanzi(item.hanzi());
        mistake.setPinyin(item.pinyin());
        mistake.setMeaning(item.meaning());
        mistake.setAudioText(item.audioText());
        mistake.setChoices(item.choicesJson());
        mistake.setAnswer(item.answer());
        mistake.setExplanation(item.explanation());
        mistake.setYourAnswer(TextUtils.trimToNull(yourAnswer));
        mistake.setSureWrong(sure);
        mistake.setStillLearning(stillLearning);
        mistake.setLastWrongAt(now);
        mistake.setClearedAt(null);
        return mistake;
    }

    record StillLearningEntry(String itemRef, GradableItem question) {
    }

    /**
     * Keeps a flashcard the learner marked "Still learning" for review, asked as what the word means ({@code question});
     * nothing was answered. Marking the same card again reopens or refreshes its row.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LearningMistake recordStillLearning(
            StudentProfile student, String itemRef, String sourceTitle, GradableItem question) {
        StudentProfile locked = accessService.lockProfile(student);
        return recordLocked(locked, MistakeSource.PRACTICE, itemRef, sourceTitle, question, null, false, true);
    }

    /**
     * Batch variant when the caller already holds {@code lockedStudent}'s profile lock: fetches all existing mistake
     * rows in one locked query, updates matched rows in place, and persists new rows with {@code saveAll}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void recordStillLearningBatchLocked(
            StudentProfile lockedStudent, String sourceTitle, List<StillLearningEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Map<String, StillLearningEntry> byRef = new LinkedHashMap<>();
        for (StillLearningEntry entry : entries) {
            byRef.putIfAbsent(entry.itemRef(), entry);
        }
        LocalDateTime now = clock.serverTimeAt(clock.now());
        Map<String, LearningMistake> existingByRef = mistakeRepository
                .findLockedByStudentIdAndItemRefIn(lockedStudent.getId(), byRef.keySet()).stream()
                .collect(Collectors.toMap(LearningMistake::getItemRef, Function.identity()));
        List<LearningMistake> created = new ArrayList<>();
        for (StillLearningEntry entry : byRef.values()) {
            LearningMistake existing = existingByRef.get(entry.itemRef());
            LearningMistake updated = populateMistake(existing, lockedStudent, MistakeSource.PRACTICE, entry.itemRef(),
                    sourceTitle, entry.question(), null, false, true, now);
            if (existing == null) {
                created.add(updated);
            }
        }
        if (!created.isEmpty()) {
            mistakeRepository.saveAll(created);
        }
    }

    /** The newest {@code size} waiting mistakes; the counts cover all of them. */
    @Transactional(readOnly = true)
    public MistakeListResponse list(SecurityUserDetails currentUser, MistakeSource source, int size) {
        StudentProfile student = accessService.requireStudent(currentUser);
        PageRequest pageable = PaginationUtils.pageRequest(0, size);
        List<LearningMistake> rows = mistakeRepository.findWaiting(student.getId(), source, pageable);
        Map<MistakeSource, Long> counts = mistakeRepository.countWaitingBySource(student.getId()).stream()
                .collect(Collectors.toMap(SourceCount::getSource, SourceCount::getTotal));
        long learn = counts.getOrDefault(MistakeSource.LEARN, 0L);
        long practice = counts.getOrDefault(MistakeSource.PRACTICE, 0L);
        long all = learn + practice;
        long total = source == null ? all : counts.getOrDefault(source, 0L);
        return new MistakeListResponse(
                new PageResponse<>(rows.stream().map(MistakeService::toRow).toList(), pageable.getPageNumber(),
                        pageable.getPageSize(), total),
                all, learn, practice);
    }

    @Transactional(readOnly = true)
    public MistakeReviewResponse review(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        List<LearningMistake> mistakes = mistakeRepository.findByStudentIdAndClearedAtIsNullOrderBySureWrongDescLastWrongAtAscIdAsc(
                student.getId(), PageRequest.of(0, REVIEW_SIZE));
        HintPolicyService.Run run = hintPolicyService.startRun(student, REVIEW_HINT_REF);
        List<QuestionView> questions = new ArrayList<>(mistakes.size());
        List<MistakeReviewResponse.ReviewContext> context = new ArrayList<>(mistakes.size());
        for (LearningMistake mistake : mistakes) {
            GradableItem item = gradable(mistake);
            questions.add(item.view(mistake.getId()));
            context.add(new MistakeReviewResponse.ReviewContext(
                    mistake.getSourceTitle(), mistake.getMistakeSource() == MistakeSource.LEARN,
                    item.shown(TextUtils.trimToNull(mistake.getYourAnswer())), mistake.isStillLearning()));
        }
        return new MistakeReviewResponse(questions, context, run.startedAt(), run.signature());
    }

    /** Instant feedback in the review; nothing is cleared until the review is finished. */
    @Transactional(readOnly = true)
    public AnswerCheckResponse check(SecurityUserDetails currentUser, QuestionAnswer answer) {
        StudentProfile student = accessService.requireStudent(currentUser);
        return gradable(waitingMistake(student, answer.questionId())).check(answer.answer());
    }

    /**
     * The one hint for a waiting mistake, as much as the student's level gives and held to its hints per set for the
     * review the server started when it was read ({@code runStartedAt}, signed). Hints never change what a review
     * clears or pays.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step hint(SecurityUserDetails currentUser, HintDto.QuestionRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        hintPolicyService.requireHintsOn(student);
        LearningMistake mistake = waitingMistake(student, request.questionId());
        return hintPolicyService.hintInRun(student, REVIEW_HINT_REF, REVIEW_HINT_REF + mistake.getId(),
                new HintPolicyService.Run(request.runStartedAt(), request.runSignature()),
                HintBuilder.ladder(gradable(mistake)));
    }

    /**
     * Clears every mistake answered right; +10 XP once a day when at least one was cleared. A mistake answered wrong
     * again stays waiting with that answer as its last one, as a wrong answer in a lesson or set records it; a skipped
     * one ("Skip": it stays in your mistakes) stays waiting as it was.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MistakeReviewCompleteResponse completeReview(
            SecurityUserDetails currentUser, MistakeReviewCompleteRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        Map<Long, String> answers =
                LearnAnswers.firstAnswers(request.answers(), QuestionAnswer::questionId, QuestionAnswer::answer);
        XpService.XpSession session = xpService.begin(student);
        LocalDateTime now = session.sessionTimestamp();
        List<LearningMistake> mistakes = mistakeRepository.findLockedWaitingByStudentIdAndIdIn(
                session.student().getId(), answers.keySet());
        int cleared = 0;
        for (LearningMistake mistake : mistakes) {
            String given = TextUtils.trimToNull(answers.get(mistake.getId()));
            if (given == null) {
                continue;
            }
            if (LearnAnswers.matches(mistake.getAnswer(), given)) {
                mistake.setClearedAt(now);
                cleared++;
            } else {
                mistake.setYourAnswer(given);
                mistake.setStillLearning(false);
                mistake.setWrongCount(mistake.getWrongCount() + 1);
                mistake.setLastWrongAt(now);
            }
        }
        if (cleared > 0) {
            session.award(XpService.SOURCE_MISTAKES_REVIEW, session.sessionDay().toEpochDay(),
                    RewardRules.MISTAKES_REVIEW_XP, "Mistakes review");
        }
        long left = mistakeRepository.countByStudentIdAndClearedAtIsNull(session.student().getId());
        return new MistakeReviewCompleteResponse(cleared, left, session.finish());
    }

    private LearningMistake waitingMistake(StudentProfile student, Long mistakeId) {
        return mistakeRepository.findByIdAndStudentIdAndClearedAtIsNull(mistakeId, student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This mistake is not on your list any more."));
    }

    static GradableItem gradable(LearningMistake mistake) {
        GradableItem stored = GradableItem.stored(
                mistake.getAnswerKind(),
                mistake.getQuestionKind(),
                mistake.getPrompt(),
                mistake.getHanzi(),
                mistake.getPinyin(),
                mistake.getMeaning(),
                mistake.getAudioText(),
                mistake.getChoices(),
                mistake.getAnswer(),
                mistake.getExplanation());
        // A typing word saved before its candidates lost their pinyin still has the answer's, which would give it away.
        return mistake.getItemRef().startsWith(PracticeType.TYPING.name() + "#") ? stored.withoutOptionPinyin() : stored;
    }

    /** Both answers read as the lesson verdict shows them ({@link GradableItem#shown}). */
    private static MistakeListResponse.Row toRow(LearningMistake mistake) {
        return new MistakeListResponse.Row(
                mistake.getId(),
                mistake.getMistakeSource(),
                mistake.getQuestionKind(),
                mistake.getPrompt(),
                mistake.getHanzi(),
                mistake.getMeaning(),
                GradableItem.shown(mistake.getAnswerKind(), mistake.getHanzi(), TextUtils.trimToNull(mistake.getYourAnswer())),
                GradableItem.shown(mistake.getAnswerKind(), mistake.getHanzi(), mistake.getAnswer()),
                mistake.isSureWrong(),
                mistake.isStillLearning());
    }
}
