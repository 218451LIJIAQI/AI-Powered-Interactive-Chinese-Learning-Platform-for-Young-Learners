package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.ai.PracticeCheckResponses;
import com.example.aichinese.entity.AiPracticeCheck;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.repository.AiPracticeCheckRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ai.AiAudioInput;
import com.example.aichinese.service.ai.AiExecutionResult;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.service.ai.AiProviderPrompt;
import com.example.aichinese.service.ai.AiUserResolver;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * Pronunciation and handwriting practice checks scored by SparkBot, for the student only: no teacher or admin
 * endpoint reads them. The recording or photo is read into memory, sent for scoring and then dropped; only the result
 * is saved (type, target text, optional lesson, score, sub-scores and feedback text).
 *
 * <p>A recording is first written down by a speech-to-text service and then compared with the target text, so tones
 * are judged from the characters that were heard; the prompt asks SparkBot to say when it is unsure.</p>
 */
@Service
public class AiPracticeCheckService {

    private static final String CHECK_NOT_FOUND = "Practice check was not found.";
    private static final String LESSON_NOT_FOUND = "Lesson was not found.";
    private static final int MAX_TARGET_LENGTH = 100;
    private static final int MAX_HEADLINE_LENGTH = 200;
    private static final int MAX_FEEDBACK_LENGTH = 1000;
    private static final int MAX_TIP_LENGTH = 300;
    private static final int MAX_TIPS = 3;
    private static final int MAX_LINKABLE_LESSONS = 50;

    private final AiUserResolver users;
    private final AiGateway gateway;
    private final FileStorageService fileStorageService;
    private final AiPracticeCheckRepository checkRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LessonProgressRepository lessonProgressRepository;
    /** The lesson of a check is read in a short transaction of its own: the AI call runs outside any transaction. */
    private final TransactionTemplate readOnlyTransaction;

    public AiPracticeCheckService(
            AiUserResolver users,
            AiGateway gateway,
            FileStorageService fileStorageService,
            AiPracticeCheckRepository checkRepository,
            StudentProfileRepository studentProfileRepository,
            LessonProgressRepository lessonProgressRepository,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.gateway = gateway;
        this.fileStorageService = fileStorageService;
        this.checkRepository = checkRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /** What the student asks SparkBot to score. {@code durationSeconds} (recordings) helps judge the pace. */
    public record CheckRequest(
            String type,
            MultipartFile file,
            String targetText,
            Long lessonId,
            Integer durationSeconds,
            String languagePreference) {
    }

    /** Scores one recording or photo; only a scored check is saved. */
    public PracticeCheckResponses.Result check(SecurityUserDetails currentUser, CheckRequest request) {
        User user = users.require(currentUser);
        PracticeCheckType type = PracticeCheckType.tryFrom(request.type())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Check type must be RECORDING or HANDWRITING."));
        String target = TextUtils.safeTruncate(AiService.requireShort(request.targetText(), "Target text is required."),
                MAX_TARGET_LENGTH);
        Lesson lesson = request.lessonId() == null ? null : linkableLesson(user, request.lessonId());
        String language = AiUserResolver.language(user, request.languagePreference());

        AiGateway.AiCall call;
        if (type == PracticeCheckType.RECORDING) {
            FileStorageService.PracticeUpload audio = fileStorageService.readPracticeAudio(request.file());
            call = AiGateway.AiCall.json(user, AiFeatureKey.PRACTICE_CHECK, language,
                            recordingPrompt(target, request.durationSeconds()), AiPracticeCheckService::scored)
                    .withAudio(new AiAudioInput(audio.data(), audio.fileName(), audio.contentType()));
        } else {
            FileStorageService.PracticeUpload photo = fileStorageService.readPracticeImage(request.file());
            call = AiGateway.AiCall.json(user, AiFeatureKey.PRACTICE_CHECK, language, handwritingPrompt(target),
                            AiPracticeCheckService::scored)
                    .withImage(new AiProviderPrompt.AiImageInput(photo.data(), photo.contentType()));
        }

        AiExecutionResult result = gateway.execute(call);
        if (!result.succeeded()) {
            return new PracticeCheckResponses.Result(result.status().name(), result.userMessage(), null);
        }
        AiPracticeCheck saved = checkRepository.save(toCheck(user, lesson, type, target, result.json()));
        return new PracticeCheckResponses.Result(result.status().name(), null, toResponse(saved));
    }

    @Transactional(readOnly = true)
    public PageResponse<PracticeCheckResponses.Check> list(SecurityUserDetails currentUser, int page, int size) {
        return PageResponse.from(
                checkRepository.findByStudentUserIdOrderByCreatedAtDescIdDesc(
                        currentUser.getUserId(), PaginationUtils.pageRequest(page, size)),
                AiPracticeCheckService::toResponse);
    }

    /** Deletes one of the student's checks; a report about it keeps its own copy of the score. */
    @Transactional
    public void delete(SecurityUserDetails currentUser, Long id) {
        checkRepository.delete(requireOwn(currentUser, id));
    }

    /** The lessons the student has opened (newest first) that are still published: a check may name one. */
    @Transactional(readOnly = true)
    public List<PracticeCheckResponses.Lesson> linkableLessons(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .map(student -> openedLessons(student).values().stream()
                        .limit(MAX_LINKABLE_LESSONS)
                        .map(lesson -> new PracticeCheckResponses.Lesson(lesson.getId(), lesson.getTitle()))
                        .toList())
                .orElse(List.of());
    }

    /** The student's own check, for a report about its score. */
    @Transactional(readOnly = true)
    public AiPracticeCheck requireOwnCheck(Long studentUserId, Long id) {
        return checkRepository.findByIdAndStudentUserId(id, studentUserId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CHECK_NOT_FOUND));
    }

    private AiPracticeCheck requireOwn(SecurityUserDetails currentUser, Long id) {
        return requireOwnCheck(currentUser.getUserId(), id);
    }

    private Lesson linkableLesson(User user, Long lessonId) {
        Lesson lesson = readOnlyTransaction.execute(status -> studentProfileRepository.findByUserId(user.getId())
                .map(student -> openedLessons(student).get(lessonId))
                .orElse(null));
        if (lesson == null) {
            throw new ResponseStatusException(NOT_FOUND, LESSON_NOT_FOUND);
        }
        return lesson;
    }

    private Map<Long, Lesson> openedLessons(StudentProfile student) {
        Map<Long, Lesson> lessons = new LinkedHashMap<>();
        for (LessonProgress progress : lessonProgressRepository.findByStudentIdOrderByUpdatedAtDesc(student.getId())) {
            Lesson lesson = progress.getLesson();
            if (ContentVisibilityPolicy.isLessonVisible(lesson)) {
                lessons.putIfAbsent(lesson.getId(), lesson);
            }
        }
        return lessons;
    }

    private static String recordingPrompt(String target, Integer durationSeconds) {
        return "Score the learner's pronunciation of the Mandarin target text \"" + target + "\"."
                + " A speech-to-text service wrote down the recording: it hears words, not exact pitch, so judge the"
                + " tones from syllables that came out as different characters or were not recognised, and say so"
                + " when you are unsure."
                + (durationSeconds == null ? "" : " The recording is " + durationSeconds + " seconds long.")
                + " Return keys: score (0-100 overall), tones (0-100), clarity (0-100: how much of the target was"
                + " recognised), pace (0-100: a natural speed for a beginner), headline (at most 8 words in the"
                + " learner's language), feedback (one or two sentences in the learner's language), tips (1 to 3"
                + " short points in the learner's language, each starting with the character and its pinyin, for"
                + " example \"谢 xiè - the 4th tone starts high and drops fast\").";
    }

    private static String handwritingPrompt(String target) {
        return "Score the learner's handwriting of \"" + target + "\" in the attached photo."
                + " Return keys: score (0-100 overall), headline (at most 8 words in the learner's language), feedback"
                + " (one or two sentences in the learner's language about stroke shape, proportion and balance), tips"
                + " (1 to 3 short points in the learner's language about what to practise next).";
    }

    private static boolean scored(Map<String, Object> json) {
        return AiJson.percent(json, "score") != null && AiJson.hasText(json, "headline");
    }

    private static AiPracticeCheck toCheck(
            User user, Lesson lesson, PracticeCheckType type, String target, Map<String, Object> json) {
        AiPracticeCheck check = new AiPracticeCheck();
        check.setStudentUser(user);
        check.setLesson(lesson);
        check.setCheckType(type);
        check.setTargetText(target);
        check.setScore(AiJson.percent(json, "score"));
        if (type == PracticeCheckType.RECORDING) {
            check.setTonesScore(AiJson.percent(json, "tones"));
            check.setClarityScore(AiJson.percent(json, "clarity"));
            check.setPaceScore(AiJson.percent(json, "pace"));
        }
        check.setHeadline(TextUtils.safeTruncate(AiJson.text(json, "headline"), MAX_HEADLINE_LENGTH));
        check.setFeedback(TextUtils.safeTruncate(AiJson.text(json, "feedback"), MAX_FEEDBACK_LENGTH));
        List<String> tips = AiJson.strings(json, "tips").stream()
                .limit(MAX_TIPS)
                .map(tip -> TextUtils.safeTruncate(tip.replace('\n', ' '), MAX_TIP_LENGTH))
                .toList();
        check.setTips(tips.isEmpty() ? null : String.join("\n", tips));
        return check;
    }

    private static PracticeCheckResponses.Check toResponse(AiPracticeCheck check) {
        Lesson lesson = check.getLesson();
        return new PracticeCheckResponses.Check(
                check.getId(),
                check.getCheckType().name(),
                check.getTargetText(),
                lesson == null ? null : lesson.getTitle(),
                check.getScore(),
                check.getTonesScore(),
                check.getClarityScore(),
                check.getPaceScore(),
                check.getHeadline(),
                check.getFeedback(),
                check.getTips() == null ? List.of() : Arrays.stream(check.getTips().split("\n")).filter(tip -> !tip.isBlank()).toList(),
                check.getCreatedAt());
    }
}
