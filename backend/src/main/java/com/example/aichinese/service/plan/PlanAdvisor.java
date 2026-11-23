package com.example.aichinese.service.plan;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PlanPace;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.ai.AiJson;
import com.example.aichinese.util.AiInputSanitizer;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * SparkBot's part of a plan, through the AI module's gateway (availability, safety check, quota and the AI-usage
 * log under "Learning plans"). It only summarises the goal, maps it to skills, orders the steps the system chose,
 * explains each step in one sentence, lists outcomes and names a first milestone for an unrealistic timeline. Every
 * id it returns is checked against the catalogue it was sent and an unknown one is dropped; an unknown level is
 * ignored. When SparkBot is unavailable (or the daily AI limit is reached) there is no draft and the plan is built
 * from rules.
 */
@Component
@RequiredArgsConstructor
public class PlanAdvisor {

    private static final int MAX_TITLE = 60;
    private static final int MAX_SUMMARY = 400;
    private static final int MAX_REASON = 220;
    private static final int MAX_ITEM = 120;
    private static final int MAX_SKILL = 40;
    private static final int MAX_MILESTONE = 300;
    private static final int MAX_OUTCOMES = 6;
    private static final int MAX_WORK_ON = 4;
    private static final int MAX_STEPS = 20;

    private final AiGateway gateway;

    public Optional<PlanComposer.AiDraft> draft(
            User user,
            String sessionId,
            String language,
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Candidates candidates,
            LocalDate today) {
        String prompt = prompt(goal, placement, candidates, today, language);
        try {
            var result = gateway.execute(AiGateway.AiCall.json(user, AiFeatureKey.LEARNING_PLAN, language, prompt,
                            json -> AiJson.hasText(json, "summary") && json.get("steps") instanceof List<?>)
                    .withSession(sessionId));
            if (!result.succeeded()) {
                return Optional.empty();
            }
            return Optional.of(parse(result.json(), candidates));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == TOO_MANY_REQUESTS.value()) {
                // The daily AI limit: the plan is still built, from rules.
                return Optional.empty();
            }
            throw exception;
        }
    }

    // Prompt

    private static String prompt(
            PlanComposer.Goal goal,
            PlanComposer.Placement placement,
            PlanComposer.Candidates candidates,
            LocalDate today,
            String language) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Help plan the Mandarin learning path of a young adult beginner on MandarinSpark.")
                .append(" The platform already chose the Learn units and the level; you only summarise the goal, map it")
                .append(" to skills, order the steps and explain them. Use only the ids listed below.\n");
        prompt.append("Main reason: ").append(word(goal.reason()));
        if (goal.reasonText() != null) {
            prompt.append(" (in their words: \"").append(goal.reasonText()).append("\")");
        }
        prompt.append(".\nGoals they picked: ").append(words(goal.effectiveGoals())).append(".\n");
        if (goal.ownWords() != null) {
            prompt.append("Their goal in their own words: \"").append(goal.ownWords()).append("\".\n");
        }
        if (goal.deadline() != null) {
            prompt.append("Deadline: ").append(goal.deadline())
                    .append(" (in about ").append(goal.weeksToDeadline(today)).append(" weeks).\n");
        } else {
            prompt.append("No deadline.\n");
        }
        prompt.append("Time they can give each day: ").append(timeBand(goal.pace())).append(".\n");
        if (!goal.focus().isEmpty()) {
            prompt.append("They want to get better at: ").append(words(goal.focus())).append(".\n");
        }
        prompt.append("Level check: ").append(levelName(placement.level()))
                .append(placement.headStart() ? " with a head start" : "");
        if (!placement.knownSkills().isEmpty()) {
            prompt.append("; already knows ").append(words(placement.knownSkills()));
        }
        if (!placement.newSkills().isEmpty()) {
            prompt.append("; new: ").append(words(placement.newSkills()));
        }
        prompt.append(".\nLearn units of the plan, required, in this order:\n");
        for (PlanCatalogue.Unit unit : candidates.units()) {
            prompt.append("- U").append(unit.id()).append(": Unit ").append(unit.number()).append(" \"")
                    .append(unit.title()).append(" ").append(unit.titleZh()).append("\" (")
                    .append(levelName(PlanComposer.levelOfPath(unit.level()))).append("; lessons: ")
                    .append(unit.lessons().stream().map(stop -> stop.title()).collect(Collectors.joining(", ")))
                    .append(")\n");
        }
        List<PlanCatalogue.ClassOption> joinedClasses = candidates.classes().stream()
                .filter(PlanCatalogue.ClassOption::joined).toList();
        List<PlanCatalogue.ClassOption> optionalClasses = candidates.classes().stream()
                .filter(option -> !option.joined()).toList();
        int maxOptional = Math.max(0, PlanComposer.MAX_PLAN_CLASSES - joinedClasses.size());
        if (!joinedClasses.isEmpty()) {
            prompt.append("Classes the student already joined (include these):\n");
            appendClassOptions(prompt, joinedClasses);
        }
        if (!optionalClasses.isEmpty() && maxOptional > 0) {
            prompt.append("Optional classes the student may add (at most ").append(maxOptional).append("):\n");
            appendClassOptions(prompt, optionalClasses);
        }
        prompt.append("Write every text in the learner's language (").append(language).append(").")
                .append(" Return JSON with keys: goalTitle (at most 6 words, e.g. \"Travel to Shanghai\"),")
                .append(" summary (one or two sentences to the student, in the second person, saying what they want to")
                .append(" be able to do), workOn (2 to 4 short skill topics the goal needs, e.g. \"food words\"),")
                .append(" targetLevel (NEW_TO_MANDARIN, BEGINNER_1 or BEGINNER_2: a realistic level for the time given),")
                .append(" steps (the plan in order: every U id above in the same order, plus up to ")
                .append(PlanComposer.MAX_PLAN_CLASSES)
                .append(" C ids placed where they help most — keeping every joined class; each item {id, reason} with")
                .append(" reason one short sentence on why the step helps")
                .append(" this goal), outcomes (4 to 6 short things they should be able to do by the end, each starting")
                .append(" with a verb), unrealistic (true only when the goal cannot be reached in the time given, such")
                .append(" as fluency in a few weeks), milestone (only when unrealistic: one sentence with a first")
                .append(" milestone they can reach by the deadline). Never promise results or grades.");
        return prompt.toString();
    }

    private static void appendClassOptions(StringBuilder prompt, List<PlanCatalogue.ClassOption> options) {
        for (PlanCatalogue.ClassOption option : options) {
            prompt.append("- C").append(option.id()).append(": \"")
                    .append(AiInputSanitizer.sanitizeShort(option.title())).append("\" (")
                    .append(TextUtils.defaultIfBlank(option.topicName(), "general"))
                    .append(option.bestAfterUnit() != null ? "; best after Unit " + option.bestAfterUnit() : "")
                    .append(option.joined() ? "; the student already joined it" : "").append("): ")
                    .append(TextUtils.defaultIfBlank(AiInputSanitizer.sanitize(option.description(), 200), ""))
                    .append("\n");
        }
    }

    /** The band the learner picked, as the setup shows it ("1–2 hours a day"), never minutes of our own. */
    static String timeBand(PlanPace pace) {
        return switch (pace) {
            case UNDER_30_MIN -> "under 30 minutes a day";
            case MINUTES_30_TO_60 -> "30–60 minutes a day";
            case HOURS_1_TO_2 -> "1–2 hours a day";
            case HOURS_2_PLUS -> "2+ hours a day";
        };
    }

    /** An enum constant as lower-case words ("order food"). */
    private static String word(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Enum constants as lower-case words, comma-separated ("order food, ask directions"). */
    private static String words(List<? extends Enum<?>> values) {
        return values.stream().map(PlanAdvisor::word).collect(Collectors.joining(", "));
    }

    private static String levelName(MandarinLevel level) {
        return switch (level) {
            case BEGINNER_2 -> "Beginner 2 (HSK 2)";
            case BEGINNER_1 -> "Beginner 1 (HSK 1)";
            default -> "New to Mandarin";
        };
    }

    // Validation

    /** Keeps only what can be trusted: known ids (each once), a known level, and texts within their limits. */
    public static PlanComposer.AiDraft parse(Map<String, Object> json, PlanComposer.Candidates candidates) {
        Set<Long> unitIds = candidates.units().stream().map(PlanCatalogue.Unit::id).collect(Collectors.toSet());
        Set<Long> classIds = candidates.classes().stream().map(PlanCatalogue.ClassOption::id).collect(Collectors.toSet());
        // By the step an id names, not its spelling: "U12" and "u012" are the same unit.
        Set<PlanComposer.StepRef> seen = new HashSet<>();
        List<PlanComposer.DraftStep> steps = new ArrayList<>();
        for (Map<?, ?> item : AiJson.objects(json, "steps", MAX_STEPS, item -> item)) {
            String id = TextUtils.trimToNull(AiJson.text(item, "id"));
            PlanStepKind kind = id == null ? null : switch (Character.toUpperCase(id.charAt(0))) {
                case 'U' -> PlanStepKind.LEARN_UNIT;
                case 'C' -> PlanStepKind.CLASS;
                default -> null;
            };
            Long refId = kind == null ? null : parseId(id.substring(1));
            boolean known = refId != null && (kind == PlanStepKind.CLASS ? classIds : unitIds).contains(refId);
            if (known && seen.add(new PlanComposer.StepRef(kind, refId))) {
                steps.add(new PlanComposer.DraftStep(kind, refId, clip(AiJson.text(item, "reason"), MAX_REASON)));
            }
        }
        MandarinLevel target = MandarinLevel.tryFrom(AiJson.text(json, "targetLevel")).orElse(null);
        boolean unrealistic = "true".equalsIgnoreCase(String.valueOf(json.get("unrealistic")));
        return new PlanComposer.AiDraft(
                clip(AiJson.text(json, "goalTitle"), MAX_TITLE),
                clip(AiJson.text(json, "summary"), MAX_SUMMARY),
                AiJson.strings(json, "workOn").stream().map(text -> clip(text, MAX_SKILL)).filter(Objects::nonNull)
                        .limit(MAX_WORK_ON).toList(),
                target,
                steps,
                AiJson.strings(json, "outcomes").stream().map(text -> clip(text, MAX_ITEM)).filter(Objects::nonNull)
                        .limit(MAX_OUTCOMES).toList(),
                unrealistic,
                unrealistic ? clip(AiJson.text(json, "milestone"), MAX_MILESTONE) : null);
    }

    private static Long parseId(String digits) {
        try {
            return Long.valueOf(digits.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** One line (PlanCodec stores lists one item per line) of at most {@code max} characters; null when blank. */
    private static String clip(String text, int max) {
        return TextUtils.safeTruncate(TextUtils.collapseWhitespaceToNull(text), max);
    }
}
