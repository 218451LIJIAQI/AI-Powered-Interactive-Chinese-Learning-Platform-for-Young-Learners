package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.HintPolicyDto;
import com.example.aichinese.entity.ServedHint;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.enums.HintMode;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.ServedHintRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/**
 * Hints by level: how long a student is stuck before a hint is offered and how many a set may use. The admin edits one
 * rule per level on AI Settings ({@code student_hint_policy}); a student's level is the one their learning plan placed
 * them at, so beginners get fuller and earlier hints. A hint is one step (A5.05): how much it says follows the level,
 * not a setting. Level checks and AI Helper stories never have hints.
 */
@Service
@RequiredArgsConstructor
public class HintPolicyService {

    public static final String POLICY_KEY = "student_hint_policy";
    /** HSK 1: after 15 s, every question; HSK 2: 30 s, 3 a set; HSK 3: 45 s, 1 a set. */
    private static final List<HintPolicyDto.Level> DEFAULT_LEVELS = List.of(
            new HintPolicyDto.Level("HSK1", 15, 0),
            new HintPolicyDto.Level("HSK2", 30, 3),
            new HintPolicyDto.Level("HSK3", 45, 1));
    private static final List<String> TIERS = DEFAULT_LEVELS.stream().map(HintPolicyDto.Level::tier).toList();
    /**
     * How far down an item's ladder ({@link HintBuilder}) a level's one hint reaches (A5.05 "What a hint gives"): HSK 1
     * rules out an answer and gives the clue; HSK 2 and HSK 3 get the first nudge only.
     */
    private static final Map<String, Integer> HINT_DEPTH = Map.of("HSK1", 2, "HSK2", 1, "HSK3", 1);

    private final SystemSettingRepository settingRepository;
    private final LearningPlanRepository planRepository;
    private final ServedHintRepository servedHintRepository;
    private final HintRunSigner runSigner;
    private final AuditLogService auditLogService;
    private final StudentLearningAccessService accessService;

    /** Default setting row seeded at startup so concurrent admin updates always lock an existing row cleanly. */
    public static SystemSetting defaultSetting() {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(POLICY_KEY);
        setting.setSettingValue(encode(DEFAULT_LEVELS));
        return setting;
    }

    // Admin

    @Transactional(readOnly = true)
    public HintPolicyDto.Response policy() {
        return new HintPolicyDto.Response(levels());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintPolicyDto.Response update(SecurityUserDetails currentUser, HintPolicyDto.UpdateRequest request) {
        Map<String, HintPolicyDto.Level> byTier = new LinkedHashMap<>();
        for (HintPolicyDto.Level level : request.levels()) {
            if (!TIERS.contains(level.tier()) || byTier.put(level.tier(), level) != null) {
                throw new ResponseStatusException(BAD_REQUEST, "Add one rule for each level: HSK 1, HSK 2 and HSK 3.");
            }
        }
        if (byTier.size() != TIERS.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add one rule for each level: HSK 1, HSK 2 and HSK 3.");
        }
        List<HintPolicyDto.Level> ordered = TIERS.stream().map(byTier::get).toList();
        List<SystemSetting> locked = settingRepository.findLockedBySettingKeyInOrderBySettingKeyAsc(List.of(POLICY_KEY));
        SystemSetting setting = locked.isEmpty() ? defaultSetting() : locked.getFirst();
        setting.setSettingValue(encode(ordered));
        settingRepository.save(setting);
        auditLogService.record(currentUser, PlatformSettingsService.SETTINGS_UPDATED_ACTION, "SYSTEM_SETTING", null,
                "Admin updated the hint rules for students.");
        return new HintPolicyDto.Response(ordered);
    }

    // Students

    @Transactional(readOnly = true)
    public HintDto.Settings settings(SecurityUserDetails currentUser) {
        return settings(accessService.requireStudent(currentUser));
    }

    private HintDto.Settings settings(StudentProfile student) {
        HintPolicyDto.Level level = levelOf(student);
        return new HintDto.Settings(student.getHintMode(), level.tier(), level.delaySeconds(), level.perSet());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Settings changeMode(SecurityUserDetails currentUser, HintMode mode) {
        StudentProfile managed = accessService.requireLockedStudent(currentUser);
        managed.setHintMode(mode);
        return settings(managed);
    }

    /**
     * The rule for the student's level: HSK 2 for a plan placed at Beginner 2, HSK 1 for any other placement and for a
     * student without a plan. The path teaches Beginner 1 and 2 only, so no student gets the HSK 3 rule yet.
     */
    private HintPolicyDto.Level levelOf(StudentProfile student) {
        MandarinLevel placed = planRepository.findActivePlacement(student)
                .orElse(MandarinLevel.NEW_TO_MANDARIN);
        String tier = placed == MandarinLevel.BEGINNER_2 ? "HSK2" : "HSK1";
        return levels().stream().filter(level -> level.tier().equals(tier)).findFirst().orElseThrow();
    }

    /** Refuses a hint when the student turned hints off. */
    public void requireHintsOn(StudentProfile student) {
        if (student.getHintMode() == HintMode.OFF) {
            throw new ResponseStatusException(FORBIDDEN, "Hints are off in your settings.");
        }
    }

    /**
     * A run of questions as the server started it: when its copy of the questions was read, and the signature a hint
     * request sends back with it ({@link #startRun}). In a request either may be missing or forged; a hint checks them.
     */
    public record Run(LocalDateTime startedAt, String signature) {
    }

    /**
     * Starts a run of the questions {@code scope} names ({@code PRACTICE#LISTENING#3#}, {@code LESSON#12#},
     * {@code MISTAKES#}). The player returns the start time and its signed run token with each hint request.
     */
    public Run startRun(StudentProfile student, String scope) {
        LocalDateTime startedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        return new Run(startedAt, runSigner.sign(student.getId(), scope, startedAt));
    }

    /**
     * The one hint for an item of a run (a question of a Learn lesson or the Mistakes review, an item of a Practice
     * set), as much as the student's level gives ({@link #HINT_DEPTH}), held to the level's hints per set: once
     * {@code perSet} (0 is a hint for every question) of the run's other items had a hint, another item gets none,
     * while the item asking may ask again and gets the same hint. Each hint given is recorded under {@code itemRef}
     * ({@code LESSON#12#345}, {@code MISTAKES#78}, {@code PRACTICE#LISTENING#3#2}), so it counts for the rest of the
     * run; a refused one is not.
     *
     * <p>The run is the one the server started when the questions were read ({@link #startRun}), checked by its
     * signature: a request cannot move its start to empty the count. Reading the questions again (a new player, or
     * Practise again) is what starts a new run, with its own hints; that is the rule, as nothing else ends a run. A
     * student's hint requests are serialised on their profile row, so parallel requests for different items cannot all
     * read a count under the limit; the transaction that calls this must be read committed (the hint endpoints' are),
     * or the count after the wait would still read the snapshot from before it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step hintInRun(StudentProfile student, String scope, String itemRef, Run run,
            List<HintDto.Step> ladder) {
        return serveInRun(student, scope, itemRef, run, ladder, null, null,
                () -> new ResponseStatusException(BAD_REQUEST, "There are no hints left in this set."));
    }

    /**
     * Variant used by Practice runs where an answered item can be retried within the same signed run ("Once more, to
     * make sure"): invokes {@code dedupeAfterLookup} after locking the student row so duplicate-row suppression only
     * considers hints recorded after the last checked answer while keeping per-set allowance single-counted per item.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step hintInRun(StudentProfile student, String scope, String itemRef, Run run,
            List<HintDto.Step> ladder, LongFunction<LocalDateTime> dedupeAfterLookup) {
        return serveInRun(student, scope, itemRef, run, ladder, null, dedupeAfterLookup,
                () -> new ResponseStatusException(BAD_REQUEST, "There are no hints left in this set."));
    }

    /**
     * Matching: the board is one question, so it has one hint: the pair of the word {@code itemRef} names. Asking again
     * for that word gives its pair again; another word of the same run gets none. The board is the run's one item, so
     * the hints per set never refuse it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step pairInRun(StudentProfile student, String scope, String itemRef, Run run,
            List<HintDto.Step> pair) {
        return serveInRun(student, scope, itemRef, run, pair, 1, null, HintPolicyService::noFurtherHint);
    }

    private HintDto.Step serveInRun(
            StudentProfile student,
            String scope,
            String itemRef,
            Run run,
            List<HintDto.Step> ladder,
            Integer fixedLimitAndDepth,
            LongFunction<LocalDateTime> dedupeAfterLookup,
            Supplier<ResponseStatusException> onLimitExceeded) {
        String runId = runSigner.requireId(student.getId(), scope, run.startedAt(), run.signature());
        StudentProfile locked = accessService.lockProfile(student);
        requireHintsOn(locked);
        int limit;
        int depth;
        if (fixedLimitAndDepth != null) {
            limit = fixedLimitAndDepth;
            depth = fixedLimitAndDepth;
        } else {
            HintPolicyDto.Level level = levelOf(locked);
            limit = level.perSet();
            depth = HINT_DEPTH.get(level.tier());
        }
        if (limit > 0 && allowanceUsed(locked.getId(), likePrefix(scope), itemRef, runId, run.startedAt(), limit)) {
            throw onLimitExceeded.get();
        }
        HintDto.Step given = HintBuilder.single(ladder, depth);
        if (given == null) {
            throw noFurtherHint();
        }
        LocalDateTime dedupeAfter = dedupeAfterLookup == null ? null : dedupeAfterLookup.apply(locked.getId());
        record(locked, itemRef, runId, dedupeAfter);
        return given;
    }

    private boolean allowanceUsed(
            Long studentId, String setRefPrefix, String itemRef, String runId, LocalDateTime runStart, int limit) {
        return runId != null
                ? servedHintRepository.allowanceUsedForRun(studentId, setRefPrefix, itemRef, runId, limit)
                : servedHintRepository.allowanceUsedForLegacyRun(studentId, setRefPrefix, itemRef, runStart, limit);
    }

    private static String likePrefix(String scope) {
        return scope.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static ResponseStatusException noFurtherHint() {
        return new ResponseStatusException(BAD_REQUEST, "There is no further hint for this item.");
    }

    /**
     * Records the hint given for the item once per run (or once since {@code dedupeAfter} when retrying an answered
     * item in the same run): re-opening the same hint does not insert duplicate rows.
     */
    private void record(StudentProfile student, String itemRef, String runId, LocalDateTime dedupeAfter) {
        if (runId != null) {
            boolean alreadyRecorded = dedupeAfter != null
                    ? servedHintRepository.existsByStudentIdAndItemRefAndRunIdAndCreatedAtAfter(
                            student.getId(), itemRef, runId, dedupeAfter)
                    : servedHintRepository.existsByStudentIdAndItemRefAndRunId(student.getId(), itemRef, runId);
            if (alreadyRecorded) {
                return;
            }
        }
        ServedHint served = new ServedHint();
        served.setStudent(student);
        served.setItemRef(itemRef);
        served.setRunId(runId);
        served.setStep(1);
        servedHintRepository.save(served);
    }

    // Storage

    private List<HintPolicyDto.Level> levels() {
        return settingRepository.findValueByKey(POLICY_KEY)
                .map(HintPolicyService::decode)
                .orElse(DEFAULT_LEVELS);
    }

    /** "HSK1:15:0;HSK2:30:3;HSK3:45:1". */
    private static String encode(List<HintPolicyDto.Level> levels) {
        return levels.stream()
                .map(level -> level.tier() + ":" + level.delaySeconds() + ":" + level.perSet())
                .collect(Collectors.joining(";"));
    }

    /**
     * A stored rule; a missing or unreadable level keeps its default. A rule saved while a hint still had steps
     * ("HSK1:15:3:0") keeps its delay and hints per set; its steps are no longer a setting.
     */
    private static List<HintPolicyDto.Level> decode(String value) {
        Map<String, HintPolicyDto.Level> byTier = new LinkedHashMap<>();
        DEFAULT_LEVELS.forEach(level -> byTier.put(level.tier(), level));
        for (String part : value.split(";")) {
            String[] fields = part.trim().split(":");
            if ((fields.length != 3 && fields.length != 4) || !byTier.containsKey(fields[0])) {
                continue;
            }
            try {
                byTier.put(fields[0], new HintPolicyDto.Level(fields[0],
                        Math.clamp(Integer.parseInt(fields[1]), 5, 120),
                        Math.clamp(Integer.parseInt(fields[fields.length - 1]), 0, 10)));
            } catch (NumberFormatException ignored) {
                // Keep the default for this level.
            }
        }
        return List.copyOf(byTier.values());
    }
}
