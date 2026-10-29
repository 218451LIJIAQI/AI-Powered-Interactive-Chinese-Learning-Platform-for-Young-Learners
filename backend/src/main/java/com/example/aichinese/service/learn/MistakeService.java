package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.MistakeListResponse;
import com.example.aichinese.dto.learn.MistakeReviewCompleteRequest;
import com.example.aichinese.dto.learn.MistakeReviewCompleteResponse;
import com.example.aichinese.dto.learn.MistakeReviewResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.entity.LearningMistake;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.util.PaginationUtils;

/**
 * Mistakes from Learn and Practice: recorded when a check finds a wrong answer, listed on the Mistakes tab and cleared
 * by the Mistakes review. A review clears the mistakes answered right and earns +10 XP once a day.
 */
@Service
public class MistakeService {

    /** Questions in one review. */
    static final int REVIEW_SIZE = 20;

    private final LearningMistakeRepository mistakeRepository;
    private final XpService xpService;
    private final LearnClock clock;

    public MistakeService(LearningMistakeRepository mistakeRepository, XpService xpService, LearnClock clock) {
        this.mistakeRepository = mistakeRepository;
        this.xpService = xpService;
        this.clock = clock;
    }

    /**
     * Keeps a wrong answer for review. The same item answered wrong again updates its waiting row, or reopens it when
     * it was cleared before.
     */
    @Transactional
    public void record(
            StudentProfile student,
            MistakeSource source,
            String itemRef,
            String sourceTitle,
            GradableItem item,
            String yourAnswer) {
        LocalDateTime now = clock.now();
        LearningMistake mistake = mistakeRepository.findLockedByStudentIdAndItemRef(student.getId(), itemRef)
                .orElse(null);
        if (mistake == null) {
            mistake = new LearningMistake();
            mistake.setStudent(student);
            mistake.setItemRef(itemRef);
            mistake.setWrongCount(1);
        } else {
            mistake.setWrongCount(mistake.getWrongCount() == null ? 1 : mistake.getWrongCount() + 1);
        }
        mistake.setMistakeSource(source);
        mistake.setSourceTitle(truncate(sourceTitle, 200));
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
        mistake.setYourAnswer(blankToNull(truncate(yourAnswer, 300)));
        mistake.setLastWrongAt(now);
        mistake.setClearedAt(null);
        mistakeRepository.save(mistake);
    }

    @Transactional(readOnly = true)
    public long waiting(StudentProfile student) {
        return mistakeRepository.countByStudentIdAndClearedAtIsNull(student.getId());
    }

    @Transactional(readOnly = true)
    public long waiting(StudentProfile student, MistakeSource source) {
        return mistakeRepository.countByStudentIdAndMistakeSourceAndClearedAtIsNull(student.getId(), source);
    }

    @Transactional(readOnly = true)
    public MistakeListResponse list(StudentProfile student, MistakeSource source, int page, int size) {
        PageRequest pageable = PaginationUtils.pageRequest(page, size);
        Page<LearningMistake> rows = source == null
                ? mistakeRepository.findByStudentIdAndClearedAtIsNullOrderByLastWrongAtDescIdDesc(student.getId(), pageable)
                : mistakeRepository.findByStudentIdAndMistakeSourceAndClearedAtIsNullOrderByLastWrongAtDescIdDesc(
                        student.getId(), source, pageable);
        long learn = waiting(student, MistakeSource.LEARN);
        long practice = waiting(student, MistakeSource.PRACTICE);
        return new MistakeListResponse(PageResponse.from(rows, MistakeService::toRow), learn + practice, learn, practice);
    }

    @Transactional(readOnly = true)
    public MistakeReviewResponse review(StudentProfile student) {
        List<LearningMistake> mistakes = mistakeRepository.findByStudentIdAndClearedAtIsNullOrderByLastWrongAtAscIdAsc(
                student.getId(), PageRequest.of(0, REVIEW_SIZE));
        return new MistakeReviewResponse(
                waiting(student),
                mistakes.stream().map(mistake -> gradable(mistake).view(mistake.getId())).toList(),
                mistakes.stream()
                        .map(mistake -> new MistakeReviewResponse.ReviewContext(
                                mistake.getSourceTitle(),
                                mistake.getMistakeSource() == MistakeSource.LEARN,
                                mistake.getYourAnswer()))
                        .toList());
    }

    /** Instant feedback in the review; nothing is cleared until the review is finished. */
    @Transactional(readOnly = true)
    public AnswerCheckResponse check(StudentProfile student, QuestionAnswer answer) {
        LearningMistake mistake = mistakeRepository
                .findByIdAndStudentIdAndClearedAtIsNull(answer.questionId(), student.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This mistake is not on your list any more."));
        return gradable(mistake).check(answer.answer());
    }

    /** Clears every mistake answered right; +10 XP once a day when at least one was cleared. */
    @Transactional
    public MistakeReviewCompleteResponse completeReview(StudentProfile student, MistakeReviewCompleteRequest request) {
        Map<Long, String> answers = new LinkedHashMap<>();
        request.answers().forEach(answer -> answers.putIfAbsent(answer.questionId(), answer.answer()));
        XpService.XpSession session = xpService.begin(student);
        LocalDateTime now = clock.now();
        List<LearningMistake> mistakes = mistakeRepository.findLockedWaitingByStudentIdAndIdIn(
                session.student().getId(), answers.keySet());
        int cleared = 0;
        for (LearningMistake mistake : mistakes) {
            if (gradable(mistake).isCorrect(answers.get(mistake.getId()))) {
                mistake.setClearedAt(now);
                cleared++;
            }
        }
        mistakeRepository.saveAll(mistakes);
        if (cleared > 0) {
            session.award(XpService.SOURCE_MISTAKES_REVIEW, clock.today().toEpochDay(),
                    RewardRules.MISTAKES_REVIEW_XP, "Mistakes review");
        }
        mistakeRepository.flush();
        return new MistakeReviewCompleteResponse(mistakes.size(), cleared, waiting(session.student()), session.finish());
    }

    static GradableItem gradable(LearningMistake mistake) {
        boolean build = mistake.getAnswerKind() == AnswerKind.BUILD;
        return new GradableItem(
                mistake.getAnswerKind(),
                mistake.getQuestionKind(),
                mistake.getPrompt(),
                mistake.getHanzi(),
                mistake.getPinyin(),
                mistake.getMeaning(),
                mistake.getAudioText(),
                build ? List.of() : LearnAnswers.readOptions(mistake.getChoices()),
                build ? LearnAnswers.readTiles(mistake.getChoices()) : List.of(),
                mistake.getAnswer(),
                mistake.getExplanation());
    }

    private static MistakeListResponse.Row toRow(LearningMistake mistake) {
        boolean build = mistake.getAnswerKind() == AnswerKind.BUILD;
        return new MistakeListResponse.Row(
                mistake.getId(),
                mistake.getMistakeSource(),
                mistake.getQuestionKind(),
                mistake.getPrompt(),
                mistake.getHanzi(),
                mistake.getMeaning(),
                build && mistake.getYourAnswer() != null
                        ? LearnAnswers.joinTiles(mistake.getYourAnswer())
                        : mistake.getYourAnswer(),
                build ? LearnAnswers.joinTiles(mistake.getAnswer()) : mistake.getAnswer(),
                mistake.getLastWrongAt());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
